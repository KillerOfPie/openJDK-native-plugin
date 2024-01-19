package org.jenkinsci.plugins.openjdk_native;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Auto-installer of native OpenJDK packages for RedHat-like distros
 * Switch to required OpenJDK version via Linux alternatives. If required OpenJDK is not installed, try to install it via yum.
 * 
 * Alternatives and yum are run via sudo, therefore appropriate sudoers setup is requited (including switching off tty requirement). 
 * Example setup:
 * <pre>
 *  #Defaults    requiretty
 *  User_Alias JENKINS = test
 *  Cmnd_Alias OPENJDK = /usr/sbin/alternatives, /usr/bin/yum
 *  JENKINS ALL = NOPASSWD: OPENJDK
 * </pre>
 * 
 * @author vjuranek
 *
 */

public class OpenJDKInstaller extends ToolInstaller{

    public static final String OPENJDK_HOME_PREFIX = "/usr/lib/jvm/";
    public static final String OPENJDK_HOME_BIN = "/bin/java";
    public static final String OPENJDK_BIN = "/usr/bin";
    
    public final OpenJDKPackage openjdkPackage;

    public UpkgInstaller upkgInstaller;
    
    @DataBoundConstructor
    public OpenJDKInstaller(OpenJDKPackage openjdkPackage) {
        super(null);
        this.openjdkPackage = openjdkPackage;
    }
    
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        String preferredPackageManager = "use to allow preferred selection later"; // TODO: Add option to select prferred package manager
        upkgInstaller = new UpkgInstaller(node.createLauncher(log), log.getLogger(), getPackageName(false));

        if(!upkgInstaller.isInstalled(preferredPackageManager)) { // If it is not installed with devel false then install
            if (!upkgInstaller.install(preferredPackageManager)) { // Install returns isInstalled boolean after completion
                upkgInstaller.setPackageName(getPackageName(true)); // If it is still not installed try again with devel true
                upkgInstaller.install(preferredPackageManager);
            } 
        }

        switchAlternatives(node, log);
        return new FilePath(node.getChannel(), OPENJDK_BIN);  //if local (on master), channel is null
    }

    private String getPackageName(boolean devel) {
        return devel ? openjdkPackage.getDevelPackageName() : openjdkPackage.getPackageName();
    }
    
    private void switchAlternatives(Node node, TaskListener log){
        log.getLogger().println("Switching to " +openjdkPackage.getPackageName() + " using alternatives ... " );
        Launcher l = node.createLauncher(log);
        try (OpenJDKConsoleAnnotator annotator = new OpenJDKConsoleAnnotator(log.getLogger())) {
            PrintStream output = log.getLogger();
            int exitStatus  = l.launch().cmds("sudo", "alternatives", "--set", "java", OPENJDK_HOME_PREFIX + "$(rpm -q " + openjdkPackage.getPackageName() +" )"+ OPENJDK_HOME_BIN).stdout(output).join();
            if(exitStatus != 0){
                byte[] errMsg = ("[OpenJDK ERROR] Switching OpenJDK via alternatives to " + openjdkPackage.getPackageName() + " failed! " + OPENJDK_BIN + " may not exists or point to different java version!\n").getBytes(Charset.defaultCharset());
                annotator.eol(errMsg,errMsg.length);
            }
        } catch (IOException e){
            e.printStackTrace();
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }
    
    @Extension
    public static class DescriptorImpl extends ToolInstallerDescriptor<OpenJDKInstaller> {

        public String getDisplayName() {
            return "OpenJDK installer";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType==JDK.class;
        }
        
        public ListBoxModel doFillOpenjdkPackageItems(){
            ListBoxModel model = new ListBoxModel();
            OpenJDKPackage[] packages = OpenJDKPackage.values();
            for(OpenJDKPackage pack : packages){
                model.add(pack.getPackageName(),pack.getName());
            }
            return model;
        }
        
    }
    
    public enum OpenJDKPackage {
        openJDK21("openJDK21","java-21-openjdk"),
        openJDK17("openJDK17","java-17-openjdk"),
        openJDK13("openJDK13","java-13-openjdk"),
        openJDK11("openJDK11","java-11-openjdk"),
        openJDK8("openJDK8","java-1.8.0-openjdk"),
        openJDK7("openJDK7","java-1.7.0-openjdk"),
        openJDK6("openJDK6","java-1.6.0-openjdk");
        
        private final String name;
        private final String packageName;
        
        OpenJDKPackage(String name, String packageName) {
           this.name = name;
           this.packageName = packageName;
        }
        
        public String getName(){
            return name;
        }
        
        public String getPackageName(){
            return packageName;
        }

        public String getDevelPackageName() {
            return packageName + "-devel";
        }
        
        public String getJreName(){
            return packageName.replaceFirst("java", "jre");
        }
    }
}
