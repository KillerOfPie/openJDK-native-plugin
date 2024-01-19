package org.jenkinsci.plugins.openjdk_native;

import java.nio.file.Files;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import hudson.Launcher;
import hudson.Proc;

/**
 * UpkgInstaller
 */
public class UpkgInstaller {

    private static DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
        public boolean accept(Path file) throws IOException {
            return (file.getFileName().toString().contains("upkg-"));
        }
    };


    HashMap<String, Path> managers;
    PrintStream output;
    String packageName;
    Launcher l;

    public UpkgInstaller(Launcher l, PrintStream output, String packageName) {
                this.managers = new HashMap<>();
                this.output = output;
                this.packageName = packageName;
                this.l = l;
                
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
                    for (Path path : stream) {
                        Proc proc = l.launch().cmds("sh", path, "manager").stdout(output).readStdout().start();
                        int exitStatus  = proc.join();
                        if(exitStatus > 0) {                    
                            managers.put(new String(proc.getStdout().readAllBytes(), StandardCharsets.UTF_8), path);
                        }
                    }
                } 
    }

    private Path selManager(String preferredManager) {
        Path selManager = managers.getOrDefault(preferredManager, managers.values().iterator().next()).toAbsolutePath();
        if(selManager == null) {
            output.println("No package managers found, there is either an error in the plugin or the system is misconfigured...");
        }

        return selManager;
    }

    private String[] baseCmd(Path selManager, String cmd) {
        return new String[]{"sh", selManager.toString() , cmd, packageName};
    }

    public boolean install(String preferredManager) {
        try (OpenJDKConsoleAnnotator annotator = new OpenJDKConsoleAnnotator(output)) {
            int exitStatus  = l.launch().cmds(baseCmd(selManager(preferredManager), "install")).stdout(output).join();
            if(exitStatus != 0){
                byte[] errMsg = ("[OpenJDK ERROR] Installation of " + packageName + " failed!").getBytes(Charset.defaultCharset());
                annotator.eol(errMsg,errMsg.length);
            }
        } catch (IOException e){
            e.printStackTrace();
        } catch (InterruptedException e){
            e.printStackTrace();
        }

        return isInstalled(preferredManager);
    }

    public boolean isInstalled(String preferredManager) {
        return l.launch().cmds(baseCmd(selManager(preferredManager), "list")).stdout(output).join() == 0;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
}