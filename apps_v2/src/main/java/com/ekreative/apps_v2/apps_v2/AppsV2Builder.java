package com.ekreative.apps_v2.apps_v2;

import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;

public class AppsV2Builder extends Builder {

	private final String projectUrl;
	private final String buildPath;

	@DataBoundConstructor
	public AppsV2Builder(String buildPath, String projectUrl) {
		this.buildPath = buildPath;
		this.projectUrl = projectUrl;
	}

	public String getBuildPath() {
		return buildPath;
	}

	public String getProjectUrl() {
		return projectUrl;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) {

		// This is where you 'build' the project.
		// Since this is a dummy, we just say 'hello world' and call that a
		// build.

		// This also shows how you can consult the global configuration of the
		// builder
		// if (getDescriptor().getUseFrench())
		listener.getLogger().println(
				"Bonjour, user!" + projectUrl + " " + buildPath);
		// else
		// listener.getLogger().println("Hello, " + name + "!");

		try {
			File buildFile = findBuildFile(buildPath, listener.getLogger());
			if (buildFile != null) {
				return sendBuild(buildFile, projectUrl,  listener.getLogger());
			}
		} catch (Exception e) {
			listener.getLogger().println(ExceptionUtils.getStackTrace(e));
		}
		return false;
	}

	private File findBuildFile(String buildPath, PrintStream logger) {
		File dir = new File(buildPath);
		FileFilter fileFilter = new WildcardFileFilter("*debug.apk");
		File[] files = dir.listFiles(fileFilter);
		Arrays.sort(files, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
			}
		});
		//TODO debug
		for (int i = 0; i < files.length; i++) {
			logger.println(files[i]);
		}
		if (files.length > 0) {
			return files[0];
		}
		return null;
	}

	private boolean sendBuild(File file, String projectUrl, PrintStream logger)
			throws IOException, ClientProtocolException {
		HttpClient httpclient = new DefaultHttpClient();
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		FileBody fileBody = new FileBody(file);
		builder.addPart("app", fileBody);
		builder.addPart("version", new StringBody("1.0.0",
				ContentType.TEXT_PLAIN));

		Header header = new BasicHeader("X-API-Key","5a61db3dcec0fe4616d08084102dad9b6511c1b1");
		HttpPost post = new HttpPost(projectUrl);
		post.setHeader(header);
		post.setEntity(builder.build());
		HttpResponse response = httpclient.execute(post);
		int status = response.getStatusLine().getStatusCode();
		logger.println("status code: " + status);
		if (status == HttpStatus.SC_OK) {
			logger.println("Successful! Build uploaded!");
			return true;
		} else {
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				logger.println(EntityUtils.toString(entity));
			}
		}
		return false;
	}

	@Extension
	public static class Descriptor extends BuildStepDescriptor<Builder> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return FreeStyleProject.class.isAssignableFrom(jobType);
		}

		@Override
		public String getDisplayName() {
			return "Upload test build to apps-v2";
		}
	}
	// private final String name;
	//
	// // Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	// @DataBoundConstructor
	// public HelloWorldBuilder(String name) {
	// this.name = name;
	// }
	//
	// /**
	// * We'll use this from the <tt>config.jelly</tt>.
	// */
	// public String getName() {
	// return name;
	// }
	//
	// @Override
	// public boolean perform(AbstractBuild build, Launcher launcher,
	// BuildListener listener) {
	// // This is where you 'build' the project.
	// // Since this is a dummy, we just say 'hello world' and call that a
	// build.
	//
	// // This also shows how you can consult the global configuration of the
	// builder
	// if (getDescriptor().getUseFrench())
	// listener.getLogger().println("Bonjour, "+name+"!");
	// else
	// listener.getLogger().println("Hello, "+name+"!");
	// return true;
	// }
	//
	// // Overridden for better type safety.
	// // If your plugin doesn't really define any property on Descriptor,
	// // you don't have to do this.
	// @Override
	// public DescriptorImpl getDescriptor() {
	// return (DescriptorImpl)super.getDescriptor();
	// }
	//
	// /**
	// * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
	// * The class is marked as public so that it can be accessed from views.
	// *
	// * <p>
	// * See
	// <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	// * for the actual HTML fragment for the configuration screen.
	// */
	// @Extension // This indicates to Jenkins that this is an implementation of
	// an extension point.
	// public static final class DescriptorImpl extends
	// BuildStepDescriptor<Builder> {
	// /**
	// * To persist global configuration information,
	// * simply store it in a field and call save().
	// *
	// * <p>
	// * If you don't want fields to be persisted, use <tt>transient</tt>.
	// */
	// private boolean useFrench;
	//
	// /**
	// * In order to load the persisted global configuration, you have to
	// * call load() in the constructor.
	// */
	// public DescriptorImpl() {
	// load();
	// }
	//
	// /**
	// * Performs on-the-fly validation of the form field 'name'.
	// *
	// * @param value
	// * This parameter receives the value that the user has typed.
	// * @return
	// * Indicates the outcome of the validation. This is sent to the browser.
	// * <p>
	// * Note that returning {@link FormValidation#error(String)} does not
	// * prevent the form from being saved. It just means that a message
	// * will be displayed to the user.
	// */
	// public FormValidation doCheckName(@QueryParameter String value)
	// throws IOException, ServletException {
	// if (value.length() == 0)
	// return FormValidation.error("Please set a name");
	// if (value.length() < 4)
	// return FormValidation.warning("Isn't the name too short?");
	// return FormValidation.ok();
	// }
	//
	// public boolean isApplicable(Class<? extends AbstractProject> aClass) {
	// // Indicates that this builder can be used with all kinds of project
	// types
	// return true;
	// }
	//
	// /**
	// * This human readable name is used in the configuration screen.
	// */
	// public String getDisplayName() {
	// return "Say hello world";
	// }
	//
	// @Override
	// public boolean configure(StaplerRequest req, JSONObject formData) throws
	// FormException {
	// // To persist global configuration information,
	// // set that to properties and call save().
	// useFrench = formData.getBoolean("useFrench");
	// // ^Can also use req.bindJSON(this, formData);
	// // (easier when there are many fields; need set* methods for this, like
	// setUseFrench)
	// save();
	// return super.configure(req,formData);
	// }
	//
	// /**
	// * This method returns true if the global configuration says we should
	// speak French.
	// *
	// * The method name is bit awkward because global.jelly calls this method
	// to determine
	// * the initial state of the checkbox by the naming convention.
	// */
	// public boolean getUseFrench() {
	// return useFrench;
	// }
	// }
}
