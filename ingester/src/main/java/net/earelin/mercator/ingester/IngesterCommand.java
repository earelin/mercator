package net.earelin.mercator.ingester;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "ingester",
    mixinStandardHelpOptions = true,
    description = "BORME historical backfill ingester."
)
public class IngesterCommand implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new IngesterCommand()).execute(args));
    }

    @Override
    public void run() {
        System.out.println("Not yet implemented.");
    }
}
