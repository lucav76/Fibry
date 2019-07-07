package eu.lucaventuri.examples;

import eu.lucaventuri.common.SystemUtils;
import eu.lucaventuri.fibry.ActorUtils;
import eu.lucaventuri.fibry.Stereotypes;

public class ChoiceExample {
    public static void main(String[] args) {
        ActorUtils.runAsFiberScope(() -> System.out.println("Naked fiber 1!"), () -> System.out.println("Naked fiber 2!"), () -> System.out.println("Naked fiber 3!"));

        ActorUtils.runAsFiber(() -> System.out.println("Naked fiber GS 1!"), () -> System.out.println("Naked fiber GS 2!"), () -> System.out.println("Naked fiber GS 3!"));

        SystemUtils.sleep(10);
        Stereotypes.threads().sink("Thread", null).execAsync(() -> System.out.println("From thread"));
        Stereotypes.auto().sink("Auto", null).execAsync(() -> System.out.println("From auto"));
        Stereotypes.fibers().sink("Fiber", null).execAsync(() -> System.out.println("From fiber"));
    }
}
