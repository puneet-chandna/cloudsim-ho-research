package org.puneet.cloudsimplus.hiippo;

import org.puneet.cloudsimplus.hiippo.runtime.RunConfig;
import org.puneet.cloudsimplus.hiippo.runtime.RunOutput;
import org.puneet.cloudsimplus.hiippo.runtime.RunCoordinator;

/** Supported executable entry point. Parsing has no output side effects. */
public final class App {
    private static final String USAGE = "Usage: java -jar cloudsim-ho-research-v2.jar --profile smoke|explore|research [--config file.properties] [--output-dir directory] [--debug]\n       --help | --version";
    public static void main(String[] args) { System.exit(run(args)); }
    static int run(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            System.out.println(USAGE); return 0;
        }
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("cloudsim-ho-research-v2 " + RunOutput.version()); return 0;
        }
        final RunConfig config;
        try { config = RunConfig.parse(args); }
        catch (IllegalArgumentException e) { System.err.println("CONFIG_ERROR: " + e.getMessage()); return 2; }
        catch (Exception e) { System.err.println("IO_ERROR: " + e.getMessage()); return 1; }
        try (var output = RunOutput.create(config)) {
            RunCoordinator.execute(config,output);
            System.out.println("COMPLETE: run retained at " + output.directory());
            return 0;
        } catch (Exception e) { System.err.println("RUN_ERROR: " + e.getMessage()); return 1; }
    }
}
