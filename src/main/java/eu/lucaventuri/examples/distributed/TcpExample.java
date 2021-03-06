package eu.lucaventuri.examples.distributed;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

public class TcpExample {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        if (args.length!=1) {
            System.out.println("Arguments: <FIBRY-PATH>");
            System.exit(101);
        }
        String fibryPath = args[0];
        var classes = new Class[]{Alice.class, Bob.class};
        var processes = new Vector<Process>();

        System.out.println("Running " + classes.length + " processes using JAR " + fibryPath);
        var fibryPathFile = new File(fibryPath);
        if (!fibryPathFile.exists()) {
            System.out.println("Jar not found in " + fibryPathFile.getAbsolutePath());
            System.exit(102);
        }

        for (var c : classes) {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", fibryPath , c.getName());
            Process p = pb.start();
            ProcessHandle ph = p.toHandle();

            p.onExit().get();
            processes.add(p);
        }

        System.out.println("* Errors:");
        for(var p: processes) {
            int n;
            var isErr = p.getErrorStream();

            if (isErr.available()==0)
                continue;

            while ((n = isErr.read()) >= 0)
                System.out.print((char) n);

            System.out.println();
        }

        System.out.println("* Output:");
        for(var p: processes) {
            int n;
            var isOut = p.getInputStream();

            while ((n = isOut.read()) >= 0)
                System.out.print((char) n);

            System.out.println();
            System.out.println();
        }
    }
}
