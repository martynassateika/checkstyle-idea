package org.infernus.idea.checkstyle;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infernus.idea.checkstyle.checker.Checkers;
import org.infernus.idea.checkstyle.checker.ScannableFile;
import org.infernus.idea.checkstyle.model.ConfigurationLocation;
import org.infernus.idea.checkstyle.ui.CheckStyleInspectionPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.infernus.idea.checkstyle.CheckStylePlugin.processError;

public class CheckStyleInspection extends LocalInspectionTool {

    private static final Log LOG = LogFactory.getLog(CheckStyleInspection.class);
    private static final ProblemDescriptor[] NO_PROBLEMS_FOUND = null;

    private final CheckStyleInspectionPanel configPanel = new CheckStyleInspectionPanel();

    private CheckStylePlugin plugin(final Project project) {
        final CheckStylePlugin checkStylePlugin = project.getComponent(CheckStylePlugin.class);
        if (checkStylePlugin == null) {
            throw new IllegalStateException("Couldn't get checkstyle plugin");
        }
        return checkStylePlugin;
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return configPanel;
    }

    @Nullable
    public ProblemDescriptor[] checkFile(@NotNull final PsiFile psiFile,
                                         @NotNull final InspectionManager manager,
                                         final boolean isOnTheFly) {
        LOG.debug("Inspection has been invoked.");

        final CheckStylePlugin plugin = plugin(manager.getProject());

        ConfigurationLocation configurationLocation = null;
        final List<ScannableFile> scannableFiles = new ArrayList<>();
        try {
            final Module module = moduleOf(psiFile);

            configurationLocation = plugin.getConfigurationLocation(module, null);
            if (configurationLocation == null || configurationLocation.isBlacklisted()) {
                return NO_PROBLEMS_FOUND;
            }

            scannableFiles.addAll(ScannableFile.createAndValidate(singletonList(psiFile), plugin, module));

            return asArray(checkers()
                    .scan(psiFile.getProject(), module, scannableFiles, configurationLocation, plugin.getConfiguration(), null, false)
                    .get(psiFile));

        } catch (ProcessCanceledException | AssertionError e) {
            LOG.warn("Process cancelled when scanning: " + psiFile.getName());
            return NO_PROBLEMS_FOUND;

        } catch (Throwable e) {
            if (configurationLocation != null) {
                configurationLocation.blacklist();
            }

            LOG.error("The inspection could not be executed.",
                    processError("The inspection could not be executed.", e));
            return NO_PROBLEMS_FOUND;

        } finally {
            scannableFiles.forEach(file -> ScannableFile.deleteIfRequired(file));
        }
    }

    private Module moduleOf(@NotNull final PsiFile psiFile) {
        return ModuleUtil.findModuleForPsiElement(psiFile);
    }

    private ProblemDescriptor[] asArray(final List<ProblemDescriptor> problems) {
        if (problems != null) {
            return problems.toArray(new ProblemDescriptor[problems.size()]);
        }
        return NO_PROBLEMS_FOUND;
    }

    private Checkers checkers() {
        return ServiceManager.getService(Checkers.class);
    }

}
