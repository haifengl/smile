/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.fife.rsta.ui.search.SearchListener;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import smile.studio.bot.BotActivity;
import smile.studio.bot.BotManager;
import smile.studio.kernel.ScalaKernel;
import smile.studio.workspace.OpenFile;
import smile.studio.workspace.Workspace;

/**
 * Regression tests for the Find and Replace menu.
 *
 * <p>A missing resource key used to make {@code SmileStudio.searchEvent} throw
 * {@link java.util.MissingResourceException} on the event dispatch thread, so
 * Find and Replace silently did nothing when a plain text file was selected.
 * These tests pin the keys and the routing contract that fixed it.
 *
 * @author Haifeng Li
 */
public class SearchResourceBundleTest {
    /** Every locale that ships a SmileStudio bundle. */
    private static final List<Locale> LOCALES = List.of(
            Locale.US,
            Locale.SIMPLIFIED_CHINESE,
            Locale.JAPAN,
            Locale.FRANCE,
            Locale.of("es", "ES"));

    /** Keys the Find and Replace menu and Agent Hub menu read at runtime. */
    private static final List<String> REQUIRED_KEYS = List.of(
            "AutoSave", "DarkTheme",
            "FindMenu", "Search", "NoActiveFile", "Find", "Replace",
            "AgentHub", "StartAgentHub", "StopAgentHub", "RestartAgentHub",
            "ConfigureBots", "BotActivity", "AgentHubNotRunning",
            "AgentHubStarted", "AgentHubStopped", "AgentHubStartFailed");

    /** Keys the Scala kernel reads when it fails to start scala-cli. */
    private static final List<String> SCALA_KERNEL_KEYS = List.of(
            "ScalaCliInstallTitle", "ScalaCliInstallMessage");

    /** Keys the Settings dialog reads at runtime, including SecretStore and Email Account status keys. */
    private static final List<String> SETTINGS_DIALOG_KEYS = List.of(
            "Title", "OK", "Cancel", "Service", "ServiceName", "ServiceProvider",
            "ServiceAdd", "ServiceRemove", "ServiceDefault",
            "APIKey", "BaseUrl", "Model",
            "Project", "Location", "StatusInEnvironment", "StatusInKeyring", "StatusInFile",
            "StatusAbsentKeyring", "StatusAbsentFile", "StatusUnavailable", "SecretSaveError",
            "TabAIServices", "TabAgentHub", "TabInferenceServer", "TabEmailAccounts",
            "HubAutoStart", "HubHost", "HubPort", "HubAdminToken", "HubKeepAliveOnExit",
            "ServeAutoStart", "ServeHost", "ServePort", "ServeModelPath", "ServeBrowse", "ServeLlm",
            "EmailAccount", "EmailAdd", "EmailRemove",
            "EmailDefault", "EmailEnabled", "EmailDescription", "EmailAddress",
            "EmailProvider", "EmailPollInterval", "EmailFolder", "EmailClientId", "EmailTenantId",
            "EmailImapHost", "EmailImapPort", "EmailSmtpHost", "EmailSmtpPort", "EmailStartTls",
            "EmailSecret", "EmailOAuthSignIn", "EmailTestConnection", "EmailTestSuccess", "EmailTestFailure");

    /** Keys the Workspace reads for sidebar and editor tabs. */
    private static final List<String> WORKSPACE_TAB_KEYS = List.of(
            "ProjectTab", "KernelTab", "BotsTab",
            "BotActivitiesTab", "BotRoutinesTab",
            "InferenceServiceTab", "AgentHubTab");

    /** Keys the BotActivity panel reads at runtime. */
    private static final List<String> BOT_ACTIVITY_KEYS = List.of(
            "CheckingHub", "StartAttachHub", "HubRunningStatus", "HubOfflineStatus",
            "BotRoutinesButton", "RefreshButton", "ConfigureRoutineMenu",
            "TabApprovalQueue", "TabRunHistory", "FilterStatusLabel", "SelectAllPendingDrafts",
            "ColSelect", "ColId", "ColKind", "ColPersona", "ColRoutine",
            "ColChannel", "ColRecipient", "ColSubject", "ColStatus", "ColExpires",
            "DraftSelectPrompt", "PromptSelectPrompt", "ToRecipientLabel", "CcLabel",
            "SubjectTitleLabel", "MessageBodyLabel", "QuestionActionDetailsLabel",
            "AnswerResolutionNoteLabel", "PreviousDraftBaselineLabel", "PreviousPayloadJsonLabel",
            "CurrentEditablePayloadLabel", "TabStructuredReview", "TabRawJson",
            "AllowOnceButton", "EditApproveButton", "AlwaysAllowRuleButton", "RulePatternLabel",
            "DenyButton", "ApproveCheckedBatchButton", "ColRunId", "ColBot", "ColModel",
            "ColStartedAt", "ColSummaryError", "SessionTranscriptLabel", "StopCancelRunButton",
            "NoPreviousVersion", "ApprovalItemDefaultTitle", "ChannelPrefix", "AccountPrefix",
            "BrowserStatePrefix", "ApprovalErrorTitle", "ApprovalErrorMessage",
            "BatchApprovalTitle", "BatchApprovalSelectMessage", "BatchApprovalErrorTitle",
            "BatchApprovalErrorMessage");

    /** Keys the BotManager panel reads at runtime. */
    private static final List<String> BOT_MANAGER_KEYS = List.of(
            "TabRoutine", "TabContextProjects", "TabReviewRules",
            "RunRoutineNowButton", "SaveButton", "EnabledCheckbox",
            "BotIdLabel", "PersonaLabel", "RoutineIdLabel", "DisplayNameLabel", "ModelOverrideLabel",
            "SectionTriggerSchedule", "SourceLabel", "MissedFirePolicyLabel", "MissedFirePolicyTooltip",
            "CronLabel", "CronTooltip", "IntervalLabel", "IntervalTooltip",
            "EndpointLabel", "EndpointTooltip",
            "QuietStartLabel", "QuietStartTooltip", "QuietEndLabel", "QuietEndTooltip",
            "SettleLabel", "SettleTooltip", "MaxTurnsPerHourLabel", "MaxTurnsPerHourTooltip",
            "SectionWorkflowEnrichment", "WorkflowKindLabel",
            "AwaitRoutineIdLabel", "AwaitRoutineIdTooltip",
            "TargetSkillToolLabel", "TargetSkillToolTooltip",
            "InstructionsLabel", "InstructionsRequiredLabel", "InstructionsOptionalLabel", "InstructionsTooltip",
            "RequireActivityCheckbox", "UseContextProjectsCheckbox",
            "SectionSessionGuardrails", "SessionModeLabel",
            "ApprovalTtlLabel", "ApprovalTtlTooltip",
            "ConfirmConsequentialCheckbox", "ColProjectName", "ColPath", "ColWorkspace", "ColPersonas",
            "ProjectsInfo", "AddProjectFolderButton", "RemoveSelectedProjectButton",
            "ColRuleId", "ColCategory", "ColPattern", "ColDecision", "RulesInfo",
            "AddRuleButton", "RemoveSelectedRuleButton", "CategoryLabel", "PatternLabel", "DecisionLabel",
            "AddAutoReviewRuleTitle", "ValidationTitle", "RoutineIdRequiredMessage",
            "TargetRequiredMessage", "InstructionsRequiredMessage", "InvalidTriggerTitle",
            "ErrorTitle", "SaveConfigErrorMessage", "RunNowTitle", "RunNowSuccessMessage",
            "RunNowErrorTitle", "RunNowErrorMessage");

    @Test
    public void testBaseBundleHasSearchKeys() {
        System.out.println("SmileStudio: base bundle defines the search keys");
        ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), Locale.ROOT);
        for (String key : REQUIRED_KEYS) {
            assertTrue(bundle.containsKey(key),
                    "base bundle is missing key: " + key);
        }
    }

    @Test
    public void testEveryLocaleHasSearchKeys() {
        System.out.println("SmileStudio: every locale defines the search keys");
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(SmileStudio.class.getName(), locale);
            for (String key : REQUIRED_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " bundle has a blank value for key: " + key);
            }
        }
    }

    @Test
    public void testOpenFileIsASearchListener() {
        System.out.println("OpenFile: is a SearchListener so the Find menu can route to any tab");
        assertTrue(SearchListener.class.isAssignableFrom(OpenFile.class),
                "OpenFile must extend SearchListener for Find/Replace to reach a text tab");
    }

    @Test
    public void testScalaKernelBundleHasEveryLocale() {
        System.out.println("ScalaKernel: every locale defines the scala-cli install hint");
        // A key added to the base bundle but not to a locale is a
        // MissingResourceException at runtime.
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(ScalaKernel.class.getName(), locale);
            for (String key : SCALA_KERNEL_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " ScalaKernel bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " ScalaKernel bundle has a blank value for key: " + key);
            }
        }
    }

    @Test
    public void testSettingsDialogBundleHasEveryLocale() {
        System.out.println("SettingsDialog: every locale defines the settings and secret-store status keys");
        ResourceBundle baseBundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), Locale.ROOT);
        for (String key : SETTINGS_DIALOG_KEYS) {
            assertTrue(baseBundle.containsKey(key),
                    "base SettingsDialog bundle is missing key: " + key);
            assertFalse(baseBundle.getString(key).isBlank(),
                    "base SettingsDialog bundle has a blank value for key: " + key);
        }
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(SettingsDialog.class.getName(), locale);
            for (String key : SETTINGS_DIALOG_KEYS) {
                assertTrue(bundle.containsKey(key),
                        locale + " SettingsDialog bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " SettingsDialog bundle has a blank value for key: " + key);
            }
        }
    }

    @Test
    public void testWorkspaceAndBotBundlesHaveEveryLocale() {
        System.out.println("Workspace, BotActivity, and BotManager: every locale defines tab and label keys");
        assertBundleKeysPresent(Workspace.class.getName(), WORKSPACE_TAB_KEYS);
        assertBundleKeysPresent(BotActivity.class.getName(), BOT_ACTIVITY_KEYS);
        assertBundleKeysPresent(BotManager.class.getName(), BOT_MANAGER_KEYS);
    }

    private static void assertBundleKeysPresent(String baseName, List<String> keys) {
        ResourceBundle baseBundle = ResourceBundle.getBundle(baseName, Locale.ROOT);
        for (String key : keys) {
            assertTrue(baseBundle.containsKey(key),
                    "base " + baseName + " bundle is missing key: " + key);
            assertFalse(baseBundle.getString(key).isBlank(),
                    "base " + baseName + " bundle has a blank value for key: " + key);
        }
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = ResourceBundle.getBundle(baseName, locale);
            for (String key : keys) {
                assertTrue(bundle.containsKey(key),
                        locale + " " + baseName + " bundle is missing key: " + key);
                assertFalse(bundle.getString(key).isBlank(),
                        locale + " " + baseName + " bundle has a blank value for key: " + key);
            }
        }
    }
}
