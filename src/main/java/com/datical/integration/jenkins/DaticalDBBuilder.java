package com.datical.integration.jenkins;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows Jenkins user to configure parameters for Datical DB and execute a
 * variety of Datical DB functions as a Build step.
 * 
 * @author <a href="mailto:info@datical.com">Robert Reeves</a>
 */
public class DaticalDBBuilder extends Builder {

	private static final Pattern WIN_ENV_VAR_REGEX = Pattern.compile("%([a-zA-Z0-9_]+)%");
	private static final Pattern UNIX_ENV_VAR_REGEX = Pattern.compile("\\$([a-zA-Z0-9_]+)");

	private final String daticalDBProjectDir;
	private final String daticalDBServer;
	private final String daticalDBAction;
	private final String daticalDBCmdProject;
	private final String daticalDBExportSQL;
	private final String daticalDBExportRollbackSQL;
	private final String daticalDBScriptDir;
	

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public DaticalDBBuilder(String daticalDBProjectDir, String daticalDBServer, String daticalDBAction, String daticalDBCmdProject, String daticalDBExportSQL, String daticalDBExportRollbackSQL, String daticalDBScriptDir) {

		this.daticalDBProjectDir = daticalDBProjectDir;
		this.daticalDBServer = daticalDBServer;
		this.daticalDBAction = daticalDBAction;
		this.daticalDBCmdProject = daticalDBCmdProject;
		this.daticalDBExportSQL = daticalDBExportSQL;
		this.daticalDBExportRollbackSQL = daticalDBExportRollbackSQL;
		this.daticalDBScriptDir = daticalDBScriptDir;

	}

	public String getDaticalDBProjectDir() {
		return daticalDBProjectDir;
	}

	public String getDaticalDBServer() {
		return daticalDBServer;
	}

	public String getDaticalDBAction() {
		return daticalDBAction;
	}
	
	public String getDaticalDBCmdProject() {
		return daticalDBCmdProject;
	}
	
	public String getDaticalDBExportSQL() {
		return daticalDBExportSQL;
	}

	public String getDaticalDBExportRollbackSQL() {
		return daticalDBExportRollbackSQL;
	}
	
	public String getDaticalDBScriptDir() {
		return daticalDBScriptDir;
	}
	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

		String UNIX_SEP = "/";
		String WINDOWS_SEP = "\\";

		listener.getLogger().println("Datical DB Global Config:");

		listener.getLogger().println("Datical DB Command = " + getDescriptor().getDaticalDBCmd());
		listener.getLogger().println("Datical DB Command (Project) = " + daticalDBCmdProject);
		listener.getLogger().println("Datical DB Drivers Dir = " + getDescriptor().getDaticalDBDriversDir());
		listener.getLogger().println("Datical DB Project Dir = " + daticalDBProjectDir);
		listener.getLogger().println("Datical DB Server = " + daticalDBServer);
		listener.getLogger().println("Datical DB Action = " + daticalDBAction);
		listener.getLogger().println("Datical DB Export SQL = " + getDaticalDBExportSQL());
		listener.getLogger().println("Datical DB Export Rollback SQL = " + getDaticalDBExportRollbackSQL());
		listener.getLogger().println("Datical DB Script Dir = " + getDaticalDBScriptDir());

		// construct the command
		String daticalCmd;
		if (daticalDBCmdProject.isEmpty()) {
			listener.getLogger().println("Using Global Datical DB Command " + getDescriptor().getDaticalDBCmd());
			daticalCmd = convertSeparator(getDescriptor().getDaticalDBCmd(), (launcher.isUnix() ? UNIX_SEP : WINDOWS_SEP));
		} else {
			listener.getLogger().println("Using Global Datical DB Command " + daticalDBCmdProject);
			daticalCmd = convertSeparator(daticalDBCmdProject, (launcher.isUnix() ? UNIX_SEP : WINDOWS_SEP));
		}
		
		String daticalDriversArg = "--drivers=" + getDescriptor().getDaticalDBDriversDir();
		String daticalProjectArg = "--project=" + daticalDBProjectDir;

		// TODO: this is less than ideal, but we need to check that daticalDBAction is set, having issues with FormValidation for daticalDBAction
		if (daticalDBAction.isEmpty()) { 
			final String errorMessage = "Datical DB Action must be set. Please update the Datical DB build step in the project's configuration.";
			listener.fatalError(errorMessage);
			return false;
		}
		// TODO: more of the same, need to get FormValidation doing this daticalDBServer check
		// forecast, snapshot, deploy, diffChangelog, diff need the DB Server set
		if (daticalDBAction.equals("forecast") || daticalDBAction.equals("snapshot") || daticalDBAction.equals("deploy") || daticalDBAction.equals("diffChangelog") || daticalDBAction.equals("diff") || daticalDBAction.equals("rollback") || daticalDBAction.equals("deploy-autoRollback") || daticalDBAction.equals("statusDetails")) {
			if (daticalDBServer.isEmpty()) {
				final String errorMessage = "Datical DB Server must be set if the Datical DB Action is \"" + daticalDBAction + "\". Please update Datical DB build step in the project's configuration.";
				listener.fatalError(errorMessage);
				return false;
			}
		}
		
		//check and encode path for spaces (windows environment)
		if (!launcher.isUnix()) {
			daticalCmd = encodeCmdLine(daticalCmd);
		}
		
		String genSQL = "";
		String genRollbackSQL = "";
		if ("true".equals(getDaticalDBExportSQL())) {
			genSQL = "--genSQL";
		}
		if ("true".equals(getDaticalDBExportRollbackSQL())) {
			if ("rollback".equals(getDaticalDBAction())) {
				genRollbackSQL = "--onlySQL";
			} else {
				genRollbackSQL = "--genRollbackSQL";
			}
		}
		
		String commandLine = "";
		String daticalDBGroovyScriptFilePrefix = "";
		if (launcher.isUnix()) {
			
		} else {
			daticalDBGroovyScriptFilePrefix = "file:/";
		}
		if (daticalDBAction.equals("groovyCreateProject")) {
			String daticalDBGroovyScript = "\"" + daticalDBGroovyScriptFilePrefix + getDaticalDBScriptDir() + (launcher.isUnix() ? UNIX_SEP : WINDOWS_SEP) + "project_creator.groovy" + "\"";
			commandLine = daticalCmd + " " + "\"" + daticalDriversArg + "\"" + " " + "\"" + daticalProjectArg + "\"" + " groovy " + daticalDBGroovyScript + " " + daticalDBServer; 
		} else if (daticalDBAction.equals("groovyBaseline")) {
			String daticalDBGroovyScript = "\"" + daticalDBGroovyScriptFilePrefix + getDaticalDBScriptDir() + (launcher.isUnix() ? UNIX_SEP : WINDOWS_SEP) + "project_baseline.groovy" + "\"";
			commandLine = daticalCmd + " " + "\"" + daticalDriversArg + "\"" + " " + "\"" + daticalProjectArg + "\"" + " groovy " + daticalDBGroovyScript + " " + daticalDBServer; 
		} else if (daticalDBAction.equals("groovyConvertSQL")) {
			String daticalDBGroovyScript = "\"" + daticalDBGroovyScriptFilePrefix + getDaticalDBScriptDir() + (launcher.isUnix() ? UNIX_SEP : WINDOWS_SEP) + "convert_sql.groovy" + "\"";
			commandLine = daticalCmd + " " + "\"" + daticalDriversArg + "\"" + " " + "\"" + daticalProjectArg + "\"" + " groovy " + daticalDBGroovyScript + " " + daticalDBServer;
		} else {
			commandLine = daticalCmd + " " + "\"" + daticalDriversArg + "\"" + " " + "\"" + daticalProjectArg + "\"" + " " + genSQL + " " + genRollbackSQL + " " + getDaticalDBActionForCmd(daticalDBAction, daticalDBServer);
		}
		
		String cmdLine = convertSeparator(commandLine, (launcher.isUnix() ? UNIX_SEP : WINDOWS_SEP));
		cmdLine = cmdLine.replace("file:\\", "file:/");

		listener.getLogger().println("File separators sanitized: " + cmdLine);

		if (launcher.isUnix()) {
			cmdLine = convertEnvVarsToUnix(cmdLine);
		} else {
			cmdLine = convertEnvVarsToWindows(cmdLine);
		}
		listener.getLogger().println("Environment variables sanitized: " + cmdLine);

		ArgumentListBuilder args = new ArgumentListBuilder();
		if (cmdLine != null) {
			args.addTokenized(cmdLine);
			listener.getLogger().println("Execute from working directory: " + args.toStringWithQuote());
		}

		if (!launcher.isUnix()) {
			args = args.toWindowsCommand();
			listener.getLogger().println("Windows command: " + args.toStringWithQuote());
		}

		EnvVars env = null;
		try {
			env = build.getEnvironment(listener);
		} catch (IOException e) {
			final String errorMessage = "Unable to find environment variables.";
			e.printStackTrace(listener.fatalError(errorMessage));
			return false;
		} catch (InterruptedException e) {
			final String errorMessage = "Unable to find environment variables.";
			e.printStackTrace(listener.fatalError(errorMessage));
			return false;
		}
		env.putAll(build.getBuildVariables());

		listener.getLogger().println("Command line: " + args.toStringWithQuote());
		listener.getLogger().println("Working directory: " + build.getWorkspace());

		try {
			final int result = launcher.decorateFor(build.getBuiltOn()).launch().cmds(args).envs(env).stdout(listener).pwd(build.getWorkspace()).join();
			return result == 0;
		} catch (final IOException e) {
			Util.displayIOException(e, listener);
			final String errorMessage = "Command execution failed";
			e.printStackTrace(listener.fatalError(errorMessage));
			return false;
		} catch (final InterruptedException e) {
			final String errorMessage = "Command execution failed";
			e.printStackTrace(listener.fatalError(errorMessage));
			return false;
		}

	}

	private String getDaticalDBActionForCmd(String daticalDBAction, String daticalDBServer) {

		// See config.jelly for all options used.
		String daticalDBActionForCmd = null;

		if (daticalDBAction.equals("checkdrivers") || daticalDBAction.equals("checkRules")) {

			daticalDBActionForCmd = daticalDBAction;
		
		} else if (daticalDBAction.equals("diffchangelog")) {
			
			daticalDBActionForCmd = "diffChangelog" + " " + daticalDBServer;
			
		} else if  (daticalDBAction.equals("setProperty")) {
			
			daticalDBActionForCmd = "set property " + daticalDBServer;
			
		} else if (daticalDBAction.equals("groovyCreateProject") || daticalDBAction.equals("groovyBaselineProject") || daticalDBAction.equals("groovyConvertSQL")) {
			
			daticalDBActionForCmd = "groovy" + daticalDBServer;
			
		} else {
			
			daticalDBActionForCmd = daticalDBAction + " " + daticalDBServer;

		}

		return daticalDBActionForCmd;

	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link DaticalDBBuilder}. Used as a singleton. The class
	 * is marked as public so that it can be accessed from views.
	 * 
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/DaticalDBBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 * 
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String daticalDBCmd;
		private String daticalDBDriversDir;

		public DescriptorImpl() {
            load();
        }

		public FormValidation doCheckDaticalDBCmd(@QueryParameter String value) throws IOException, ServletException {

			if (value.length() == 0)
				return FormValidation.error("Please set the Datical DB Command");
			return FormValidation.ok();

		}

		public FormValidation doCheckDaticalDBDriversDir(@QueryParameter String value) throws IOException, ServletException {

			if (value.length() == 0)
				return FormValidation.error("Please set the Datical DB Drivers Directory");
			return FormValidation.ok();

		}

		public FormValidation doCheckDaticalDBProjectDir(@QueryParameter String value) throws IOException, ServletException {

			if (value.length() == 0)
				return FormValidation.error("Please set the Datical DB Project Directory");
			return FormValidation.ok();

		}
		
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Datical DB";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			daticalDBCmd = formData.getString("daticalDBCmd");
			daticalDBDriversDir = formData.getString("daticalDBDriversDir");

			// ^Can also use req.bindJSON(this, formData);
			// (easier when there are many fields; need set* methods for this,
			// like setUseFrench)
			save();
			return super.configure(req, formData);
		}

		public String getDaticalDBCmd() {
			return daticalDBCmd;
		}

		public String getDaticalDBDriversDir() {
			return daticalDBDriversDir;
		}
		
	}

	public static String convertSeparator(String cmdLine, String newSeparator) {
		String match = "[/" + Pattern.quote("\\") + "]";
		String replacement = Matcher.quoteReplacement(newSeparator);

		Pattern words = Pattern.compile("\\S+");
		Pattern urls = Pattern.compile("(https*|ftp|git):");
		StringBuffer sb = new StringBuffer();
		Matcher m = words.matcher(cmdLine);
		while (m.find()) {
			String item = m.group();
			if (!urls.matcher(item).find()) {
				// Not sure if File.separator is right if executing on slave
				// with OS different from master's one
				// String cmdLine = commandLine.replaceAll("[/\\\\]",
				// File.separator);
				m.appendReplacement(sb, Matcher.quoteReplacement(item.replaceAll(match, replacement)));
			}
		}
		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Corrects Windows Path Issues containing spaces.  E.g,
	 * Assuming a standard installation of DaticalDB
	 * C:\Program Files\DaticalDB
	 * 
	 * @return The command with encoded double quotes.
	 */
	
	public static String encodeCmdLine(String cmdLine){
		
		String REGEX = "\\s+";
		
		Pattern p = Pattern.compile(REGEX);
		String[] s = p.split(cmdLine);
		
		//if no spaces then just return the path as is
		if (s.length == 1) {
			
			return cmdLine;
			
		}
		
		//otherwise we need to escape the path
		StringBuffer sb = new StringBuffer();
		
		for(int x = 0; x < s.length; x++)
		{
			if (x == 0){
				sb.append("\"").append(s[x]).append(" ");
				
			}else{
				if (x == (s.length -1)){
					sb.append(s[x]).append("\"");
				}else{
					sb.append(s[x]).append(" ");
				}
				
			}
			
		}
		
		return sb.toString();
		
	}
	
	/**
	 * Convert Windows-style environment variables to UNIX-style. E.g.
	 * "script --opt=%OPT%" to "script --opt=$OPT"
	 * 
	 * @param cmdLine
	 *            The command line with Windows-style env vars to convert.
	 * @return The command line with UNIX-style env vars.
	 */
	public static String convertEnvVarsToUnix(String cmdLine) {
		if (cmdLine == null) {
			return null;
		}

		StringBuffer sb = new StringBuffer();

		Matcher m = WIN_ENV_VAR_REGEX.matcher(cmdLine);
		while (m.find()) {
			m.appendReplacement(sb, "\\$$1");
		}
		m.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Convert UNIX-style environment variables to Windows-style. E.g.
	 * "script --opt=$OPT" to "script --opt=%OPT%"
	 * 
	 * @param cmdLine
	 *            The command line with Windows-style env vars to convert.
	 * @return The command line with UNIX-style env vars.
	 */
	public static String convertEnvVarsToWindows(String cmdLine) {
		if (cmdLine == null) {
			return null;
		}

		StringBuffer sb = new StringBuffer();

		Matcher m = UNIX_ENV_VAR_REGEX.matcher(cmdLine);
		while (m.find()) {
			m.appendReplacement(sb, "%$1%");
		}
		m.appendTail(sb);

		return sb.toString();
	}
}
