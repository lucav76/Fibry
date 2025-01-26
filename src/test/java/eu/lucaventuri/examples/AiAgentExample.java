package eu.lucaventuri.examples;

import eu.lucaventuri.fibry.ai.*;
import static eu.lucaventuri.common.RecordUtils.replaceAllFields;
import static eu.lucaventuri.common.RecordUtils.replaceField;
import static eu.lucaventuri.common.RecordUtils.replaceFields;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AiAgentExample {
    public static class AiAgentVacations {
        private static final String promptFood = "You are a foodie from {country}. Please tell me the top 10 cities for food in {country}.";
        private static final String promptActivity = "You are from {country}, and know it inside out. Please tell me the top 10 cities in {country} where I can {goal}";
        private static final String promptSea = "You are an expert traveler, and you {country} inside out. Please tell me the top 10 cities for sea vacations in {country}.";
        private static final String promptChoice = """
                You enjoy traveling, eating good food and staying at the sea, but you also want to {activity}. Please analyze the following suggestions from your friends for a vacation in {country} and choose the best city to visit, offering the best mix of food and sea and where you can {activity}.
                Food suggestions: {food}.
                Activity suggestions: {activity}.
                Sea suggestions: {sea}.
                """;

        enum VacationStates {
            CITIES, CHOICE
        }
        public record VacationContext(String country, String goal, String food, String activity, String sea, String proposal) {
            public static VacationContext from(String country, String goal) {
                return new VacationContext(country, goal, null, null, null, null);
            }
        }

        public static AiAgent<?, VacationContext> buildAgent(LLM modelSearch, LLM modelThink) {
            AgentNode<VacationStates, VacationContext> nodeFood = state -> state.setAttribute("food", modelSearch.call("user", replaceField(promptFood, state.data(), "country")));
            AgentNode<VacationStates, VacationContext> nodeActivity = state -> state.setAttribute("activity", modelSearch.call("user", replaceFields(promptActivity, state.data(), "country", "goal")));
            AgentNode<VacationStates, VacationContext> nodeSea = state -> state.setAttribute("sea", modelSearch.call("user", replaceField(promptSea, state.data(), "country")));
            AgentNode<VacationStates, VacationContext> nodeChoice = state -> {
                var prompt = replaceAllFields(promptChoice, state.data());
                System.out.println("***** CHOICE PROMPT: " + prompt);
                return state.setAttribute("proposal", modelThink.call("user", prompt));
            };

            var builder = AiAgent.<VacationStates, VacationContext>builder(true);
            builder.addStateParallel(VacationStates.CITIES, VacationStates.CHOICE, 1, List.of(nodeFood, nodeActivity, nodeSea), null);
            builder.addState(VacationStates.CHOICE, null, 1, nodeChoice, null);

            return builder.build(VacationStates.CITIES, null, false);
        }
    }

    public static class AiAgentTravelAgency {
        private static final String promptDestination = "Read the following text describing a destination for a vacation and extract the destination as a simple city and country, no preamble. Just the city and the country. {proposal}";
        private static final String promptCost = "You are an expert travel agent. A customer asked you to estimate the cost of travelling from {startCity}, {startCountry} to {destination}, for {adults} adults and {kids} kids}";

        enum TravelStates {
            SEARCH, CALCULATE
        }

        public record TravelContext(String startCity, String startCountry, int adults, int kids, String destination, String cost, String proposal) { }

        public static AiAgent<?, TravelContext> buildAgent(LLM model, AiAgent<?, AiAgentVacations.VacationContext> vacationsAgent, String country, String goal, boolean debugSubAgentStates) {
            var builder = AiAgent.<TravelStates, TravelContext>builder(false);
            AgentNode<TravelStates, TravelContext> nodeSearch = state -> {
                var vacationProposal = vacationsAgent.process(AiAgentVacations.VacationContext.from(country, goal), 1, TimeUnit.MINUTES, (st, info) -> System.out.print(debugSubAgentStates ? st + ": " + info : ""));
                return state.setAttribute("proposal", vacationProposal.proposal())
                        .setAttribute("destination", model.call(promptDestination.replaceAll("\\{proposal\\}", vacationProposal.proposal())));
            };
            AgentNode<TravelStates, TravelContext> nodeCalculateCost = state -> state.setAttribute("cost", model.call(replaceAllFields(promptCost, state.data())));

            builder.addState(TravelStates.SEARCH, TravelStates.CALCULATE, 1, nodeSearch, null);
            builder.addState(TravelStates.CALCULATE, null, 1, nodeCalculateCost, null);

            return builder.build(TravelStates.SEARCH, null, false);
        }
    }


    public static void main(String[] args) {
        try (var vacationsAgent = AiAgentVacations.buildAgent(ChatGPT.GPT_MODEL_4O, ChatGPT.GPT_MODEL_O1_MINI)) {
            try (var travelAgent = AiAgentTravelAgency.buildAgent(ChatGPT.GPT_MODEL_4O, vacationsAgent, "Italy", "Dance Salsa and Bachata", true)) {
                var result = travelAgent.process(new AiAgentTravelAgency.TravelContext("Oslo", "Norway", 2, 2, null, null, null), (state, info) -> System.out.println(state + ": " + info));

                System.out.println("\n\n\n***** FINAL ANALYSIS *****\n\n\n");
                System.out.println("\n\n\n*** Destination: " + result.destination());
                System.out.println("*** Proposal: " + result.proposal());
                System.out.println("\n\n\n*** Cost: " + result.cost());
            }
        }
    }
}
