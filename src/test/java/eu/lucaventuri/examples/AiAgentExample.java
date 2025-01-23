package eu.lucaventuri.examples;

import eu.lucaventuri.common.RecordUtils;
import eu.lucaventuri.fibry.ai.*;
import static eu.lucaventuri.common.RecordUtils.replaceAllFields;
import static eu.lucaventuri.common.RecordUtils.replaceField;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AiAgentExample {
    public static class AiAgentVacations {
        private static final String promptFood = "You are an foodie from {country}. Please tell me the top 10 cities for food in {country}.";
        private static final String promptDance = "You are an dancer from {country}. Please tell me the top 10 cities in {country} where I can dance Salsa and Bachata.";
        private static final String promptSea = "You are an expert traveler, and you {country} inside out. Please tell me the top 10 cities for sea vacations in {country}.";
        private static final String promptChoice = """
                You enjoy traveling, dancing Salsa and Bachata, eating good food and staying at the sea. Please analyze the following suggestions from your friends for a vacation in {country} and choose the best city to visit, offering the best mix of Salsa and Bachata dancing, food and sea.
                Food suggestions: {food}.
                Dance suggestions: {dance}.
                Sea suggestions: {sea}.
                """;

        enum VacationStates {
            CITIES, CHOOSE
        }
        public record VacationContext(String country, String food, String dance, String sea, String proposal) {
            public static VacationContext from(String country) {
                return new VacationContext(country, null, null, null, null);
            }
        }

        public static AiAgent<?, VacationContext> buildAgent(LLM modelSearch, LLM modelThink) {
            var builder = new AiAgentBuilderActor<VacationStates, VacationContext>(false);
            AgentNode<VacationStates, VacationContext> nodeFood = ctx -> ctx.info.replace("food", modelSearch.call("user", replaceField(promptFood, ctx.info.getState(), "country")));
            AgentNode<VacationStates, VacationContext> nodeDance = ctx -> ctx.info.replace("dance", modelSearch.call("user", replaceField(promptDance, ctx.info.getState(), "country")));
            AgentNode<VacationStates, VacationContext> nodeSea = ctx -> ctx.info.replace("sea", modelSearch.call("user", replaceField(promptSea, ctx.info.getState(), "country")));
            AgentNode<VacationStates, VacationContext> nodeChoice = ctx -> {
                var prompt = replaceAllFields(promptChoice, ctx.info.getState());
                System.out.println("***** CHOICE PROMPT: " + prompt);
                return ctx.info.replace("proposal", modelThink.call("user", prompt));
            };

            builder.addStateParallel(VacationStates.CITIES, VacationStates.CHOOSE, 1, List.of(nodeFood, nodeDance, nodeSea), null);
            builder.addState(VacationStates.CHOOSE, null, 1, nodeChoice, null);

            return builder.build(VacationStates.CITIES, null);
        }
    }

    public static class AiAgentTravelAgency {
        private static final String promptDestination = "Read the following text describing a destination for a vacation and extract the destination as a simple city and country, no preamble. Just the city and the country. {proposal}";
        private static final String promptCost = "You are an expert travel agent. A customer asked you to estimate the cost of travelling from {startCity}, {startCountry} to {destination}, for {adults} adults and {kids} kids}";
        enum TravelStates {
            SEARCH, CALCULATE
        }

        public record TravelContext(String startCity, String startCountry, String destination, int adults, int kids, String cost, String proposal) {
            public static TravelContext from(String startCity, String startCountry, int adults, int kids) {
                return new TravelContext(startCity, startCountry, null, adults, kids, null, null);
            }
        }

        public static AiAgent<?, TravelContext> buildAgent(LLM model, AiAgent<?, AiAgentVacations.VacationContext> vacationsAgent, String country) {
            var builder = new AiAgentBuilderActor<TravelStates, TravelContext>(false);
            AgentNode<TravelStates, TravelContext> nodeSearch = ctx -> {
                var vacationProposal = vacationsAgent.sendMessageReturnWait(AiAgentVacations.VacationContext.from(country), null, 1, TimeUnit.MINUTES).result();
                return ctx.info.replace("proposal", vacationProposal.proposal())
                        .replace("destination", model.call(promptDestination.replaceAll("\\{proposal\\}", vacationProposal.proposal())));
            };
            AgentNode<TravelStates, TravelContext> nodeCalculateCost = ctx -> ctx.info.replace("cost", model.call(replaceAllFields(promptCost, ctx.info.getState())));

            builder.addState(TravelStates.SEARCH, TravelStates.CALCULATE, 1, nodeSearch, null);
            builder.addState(TravelStates.CALCULATE, null, 1, nodeCalculateCost, null);

            return builder.build(TravelStates.SEARCH, null);
        }
    }


    public static void main(String[] args) {
        try (var vacationsAgent = AiAgentVacations.buildAgent(ChatGpt.GPT_MODEL_4O, ChatGpt.GPT_MODEL_O1_MINI)) {
            try (var travelAgent = AiAgentTravelAgency.buildAgent(ChatGpt.GPT_MODEL_4O, vacationsAgent, "Italy")) {
                var result = travelAgent.execute(AiAgentTravelAgency.TravelContext.from("Oslo", "Norway", 2,2), (state, info) -> System.out.println(state + ": " + info));

                System.out.println("\n\n\n***** FINAL ANALYSIS *****\n\n\n");
                System.out.println(result.result().proposal);
                System.out.println(result.result().destination);
                System.out.println(result.result().cost());
            }
        }
    }
}
