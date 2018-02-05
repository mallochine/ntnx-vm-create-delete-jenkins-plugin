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

public class HelloWorldBuilder extends Builder implements SimpleBuildStep {
  // Image UUID.
  private final String imageUuid;

  // VM Name.
  private final String vmName;

  // This field is selected from a drop-down, so the string can be only of
  // certain values. Like a enum.
  private final String vmOp;

  @DataBoundConstructor
  public HelloWorldBuilder(String imageUuid, String vmName, String vmOp) {
    this.imageUuid = imageUuid;
    this.vmName = vmName;
    this.vmOp = vmOp;
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
    // Make API call.
  }

  @Symbol("greet")
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

    public FormValidation doCheckImageUuid(
      @QueryParameter String imageUuid,
      @QueryParameter String vmName,
      @QueryParameter String vmOp)
      throws IOException, ServletException {

      if (vmName.isEmpty()) {
        return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingVmName());
      }
      if (vmOp.isEmpty()) {
        return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingVmOp());
      }
      if (vmOp.equals("create") && imageUuid.isEmpty()) {
        // TODO: check that 'imageUuid' is a valid UUID.
        return FormValidation.error(Messages.HelloWorldBuilder_DescriptorImpl_errors_missingImageUuid());
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
}
