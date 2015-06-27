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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;

public class AppsV2Builder extends Builder {

	private static final String VERSION_NAME = "versionName \"";
	private final String appName;
	private final String projectUrl;
	private final String buildPath;

	@DataBoundConstructor
	public AppsV2Builder(String appName, String buildPath, String projectUrl) {
		this.appName = appName;
		this.buildPath = buildPath;
		this.projectUrl = projectUrl;
	}
	

	public String getAppName() {
		return appName;
	}


	public String getBuildPath() {
		return buildPath;
	}

	public String getProjectUrl() {
		return projectUrl;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		listener.getLogger().println("Launch APPS-V2 plugin #ekreativehackday15");
		listener.getLogger().println("Project Url=" + projectUrl);
		listener.getLogger().println("Project Path=" + buildPath);
		String pathToFile = buildPath + "\\build\\outputs\\apk";
		listener.getLogger().println("Path to build file=" + pathToFile);
		try {
			File buildFile = findBuildFile(pathToFile, listener.getLogger());
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
		//just show files
		for (int i = 0; i < files.length; i++) {
			logger.println(files[i]);
		}
		if (files.length > 0) {
			return files[0];
		}
		return null;
	}
	
	public String getAppVersion(String buildPath, PrintStream logger) {
		File gradleFile = new File(buildPath+"\\build.gradle");
		try {
			String content = new Scanner(gradleFile).useDelimiter("\\Z").next();
			int startVesion = content.indexOf(VERSION_NAME);
			if (startVesion != -1) {
				String substing = content.substring(startVesion+VERSION_NAME.length());
				return substing.substring(0, substing.indexOf("\""));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return "unknown";
	}
	
	public File getAppIcon(String buildPath, PrintStream logger) {
		File mipmapFile = new File(buildPath+"\\src\\main\\res\\mipmap-xxhdpi\\ic_launcher.png");
		if (mipmapFile.exists()) {
			return mipmapFile;
		} else {
			File drawableFile = new File(buildPath+"\\src\\main\\res\\drawable-xxhdpi\\ic_launcher.png");
			if (drawableFile.exists()) {
				return drawableFile;
			}
		}
		return null;
	}

	private boolean sendBuild(File appFile, String projectUrl, PrintStream logger) throws IOException, ClientProtocolException {
		HttpClient httpclient = new DefaultHttpClient();
		String appVersion = getAppVersion(buildPath, logger);
		logger.println("appVersion="+appVersion);
		File appIcon = getAppIcon(buildPath, logger);
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		FileBody appFileBody = new FileBody(appFile);
		builder.addPart("app", appFileBody);
		if (appIcon != null) {
			FileBody iconFileBody = new FileBody(appIcon);
			builder.addPart("icon", iconFileBody);
		}
		builder.addPart("name", new StringBody(appName.isEmpty() ? "unknown": appName, ContentType.TEXT_PLAIN));
		builder.addPart("version", new StringBody(appVersion, ContentType.TEXT_PLAIN));

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
			logger.println("Build upload failde!");
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
}
