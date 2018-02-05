package org.jenkinsci.plugins.sample;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import java.util.UUID;

// Java SDK imports.
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.BufferedReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import javax.xml.bind.DatatypeConverter;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

public class NtnxVmPlugin extends Builder implements SimpleBuildStep {
  PrintStream logger;

  // Cluster IP.
  private final String clusterIp;

  // Nutanix username.
  private final String username;

  // Nutanix password.
  private final String password;

  // Image UUID.
  private final String imageUuid;

  // VM Name.
  private final String vmName;

  // This field is selected from a drop-down, so the string can be only of
  // certain values. Like a enum.
  private final String vmOp;

  // String nums for VM operations.
  private static final String OP_CREATE = "create";
  private static final String OP_POWERON = "poweron";
  private static final String OP_DELETE = "delete";

  @DataBoundConstructor
  public NtnxVmPlugin(
    String clusterIp, String ntnxUsername, String ntnxPassword, String imageUuid,
    String vmName, String vmOp) {

    this.clusterIp = clusterIp;
    this.username = ntnxUsername;
    this.password = ntnxPassword;
    this.imageUuid = imageUuid;
    this.vmName = vmName;
    this.vmOp = vmOp;
  }

  @Override
  public void perform(
    Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
    throws InterruptedException, IOException {

    // https://stackoverflow.com/questions/19540289/how-to-fix-the-java-security-cert-certificateexception-no-subject-alternative
    disableSslVerification();

    // Switch statement on on 'vmOp'.
    this.logger = listener.getLogger();
    if (vmOp.equals(OP_CREATE)) { CreateVm(vmName); }
    if (vmOp.equals(OP_POWERON)) { PowerOnVm(vmName); }
    if (vmOp.equals(OP_DELETE)) { DeleteVm(vmName); }
  }

  // Implement create op.
  public void CreateVm(final String vmName) {
    logger.println("Creating VM with name '" + vmName + "'");
    final String body = "{" +
      "\"name\":\"" + vmName + "\"," +
      "\"memory_mb\":1024," +
      "\"num_vcpus\":1," +
      "\"description\":\"\"," +
      "\"num_cores_per_vcpu\":1," +
      "\"vm_disks\":[" +
      "  {" +
      "    \"is_cdrom\":false," +
      "    \"disk_address\":{" +
      "      \"device_bus\":\"scsi\"" +
      "      }," +
      "    \"vm_disk_clone\":{" +
      "      \"disk_address\":{" +
      "        \"vmdisk_uuid\":\"" + imageUuid + "\"" +
      "        }" +
      "      }" +
      "    }" +
      "  ]" +
      "}";
    logger.println(body);
    JSONObject ret = RestCall("/vms/", "POST", body);
    logger.println(ret.toString());
  }

  // Implement power-on op.
  public void PowerOnVm(final String vmName) {
    logger.println("Powering on VM with name '" + vmName + "'");
    final String vmUuid = GetVmUuid(vmName);
    if (vmUuid == null) {
      logger.println("Could not get VM UUId for name '" + vmName + "'");
      return;
    }
    final String body = "{\"transition\": \"ON\"}";
    RestCall("/vms/" + vmUuid + "/set_power_state/", "POST", body);
    // TODO: check whether power-on was successful.
  }

  // Implement delete op.
  public void DeleteVm(final String vmName) {
    logger.println("Deleting VM with name '" + vmName + "'");
    final String vmUuid = GetVmUuid(vmName);
    if (vmUuid == null) {
      logger.println("Could not get VM UUId for name '" + vmName + "'");
      return;
    }
    JSONObject ret = RestCall("/vms/" + vmUuid, "DELETE", "");
    logger.println(ret.toString());
    // TODO: check whether delete was successful
  }

  public String GetVmUuid(final String vmName) {
    // Get VMs and find VM with matching VM name.
    try {
      final JSONObject json = RestCall("/vms", "GET", "");
      final JSONArray vms = json.getJSONArray("entities");
      for (int i = 0; i < vms.length(); ++i) {
        final JSONObject vm = vms.getJSONObject(i);
        final String name = vm.getString("name");
        if (name.equals(vmName)) {
          return vm.getString("uuid");
        }
      }
    } catch (org.json.JSONException e) {
      logger.println("Some JSON error while parsing VM UUID\n" + e.toString());
    }
    return null;
  }

  public JSONObject RestCall(
    final String urlPath,
    final String requestMethod,
    final String requestBody) {

    String webPage = "https://" + clusterIp +
      ":9440/PrismGateway/services/rest/v2.0" + urlPath;
		String authString = username + ":" + password;
    try {
      URL url = new URL(webPage);
      String encoding = DatatypeConverter.printBase64Binary(
        authString.getBytes("UTF-8"));

      // Setup connection.
      HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod(requestMethod);
      connection.setRequestProperty("Authorization", "Basic " + encoding);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Content-Type", "application/json");

      // Write request body.
      if (!requestBody.isEmpty()) {
        OutputStream os = connection.getOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
        osw.write(requestBody);
        osw.flush();
        osw.close();
        os.close();
        connection.connect();
      }

      // Read response body.
      InputStream content = (InputStream)connection.getInputStream();
      BufferedReader in =
        new BufferedReader (new InputStreamReader (content, "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = in.readLine()) != null) {
        sb.append(line);
      }
      in.close();

      // Parse JSON response.
      try {
        return new JSONObject(sb.toString());
      } catch (org.json.JSONException e) {
        logger.println("Couldn't parse JSON for urlPath " + urlPath +
          ", requestMethod " + requestMethod +
          ", requestBody " + requestBody);
        logger.println("Exception:" + e.toString());
        e.printStackTrace();
      }
      return null;
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Symbol("greet")
  @Extension
  public static final class DescriptorImpl
    extends BuildStepDescriptor<Builder> {

    public FormValidation doCheckImageUuid(
      @QueryParameter String clusterIp,
      @QueryParameter String ntnxUsername,
      @QueryParameter String ntnxPassword,
      @QueryParameter String imageUuid,
      @QueryParameter String vmName,
      @QueryParameter String vmOp)
      throws IOException, ServletException {

      if (clusterIp.isEmpty()) {
        return FormValidation.error(
          Messages.NtnxVmPlugin_DescriptorImpl_errors_missingClusterIp());
      }
      if (ntnxUsername.isEmpty()) {
        return FormValidation.error(
          Messages.NtnxVmPlugin_DescriptorImpl_errors_missingNtnxUsername());
      }
      if (ntnxPassword.isEmpty()) {
        return FormValidation.error(
          Messages.NtnxVmPlugin_DescriptorImpl_errors_missingNtnxPassword());
      }
      if (vmName.isEmpty()) {
        return FormValidation.error(
          Messages.NtnxVmPlugin_DescriptorImpl_errors_missingVmName());
      }
      if (vmOp.isEmpty()) {
        return FormValidation.error(
          Messages.NtnxVmPlugin_DescriptorImpl_errors_missingVmOp());
      }
      if (vmOp.equals(OP_CREATE) && imageUuid.isEmpty()) {
        // TODO: check that 'imageUuid' is a valid UUID.
        return FormValidation.error(
          Messages.NtnxVmPlugin_DescriptorImpl_errors_missingImageUuid());
      }

      return FormValidation.ok();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Create/power on/delete VM";
    }
  }

  // https://stackoverflow.com/questions/19540289/how-to-fix-the-java-security-cert-certificateexception-no-subject-alternative
  private void disableSslVerification() {
    try
    {
      // Create a trust manager that does not validate certificate chains
      TrustManager[] trustAllCerts = new TrustManager[] {
          new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
          }
          public void checkClientTrusted(
            X509Certificate[] certs, String authType) {
          }
          public void checkServerTrusted(
            X509Certificate[] certs, String authType) {
          }
        }
      };

      // Install the all-trusting trust manager
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

      // Create all-trusting host name verifier
      HostnameVerifier allHostsValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      };

      // Install the all-trusting host verifier
      HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (KeyManagementException e) {
      e.printStackTrace();
    }
  }
}
