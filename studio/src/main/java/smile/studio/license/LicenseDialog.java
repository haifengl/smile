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
package smile.studio.license;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.View;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import smile.license.LicenseKey;
import smile.license.LicenseVerificationException;
import smile.studio.text.Monospaced;

/**
 * First-run and on-demand modal dialog for SMILE Agent Studio license activation
 * and BSL 1.1 usage-mode attestation.
 *
 * <p>Presents two mutually exclusive radio cards:
 * <ol>
 *   <li><b>Personal, Academic, or Non-Production Evaluation (BSL 1.1)</b> — 1-click entry.</li>
 *   <li><b>Commercial Production Use</b> — offline Ed25519 license key activation or
 *       30-day self-service commercial trial.</li>
 * </ol>
 *
 * @author Haifeng Li
 */
public class LicenseDialog extends JDialog {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LicenseDialog.class);

    /** Resource bundle basename for LicenseDialog localization. */
    public static final String BUNDLE_NAME = "smile.studio.license.License";

    /** URL opened when clicking "Get a License Key". */
    public static final String LICENSING_URL = "https://www.aihalo.dev/licensing.html";

    /** URL opened when clicking the studio/LICENSE link in the footer. */
    public static final String LICENSE_URL = "https://github.com/haifengl/smile/blob/master/studio/LICENSE";

    private static final int DEFAULT_TEXT_WIDTH = 560;
    private static final ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault());

    private final LicenseStore store;
    private LicenseState state;
    private final boolean startupGate;

    final JRadioButton evalRadio = new JRadioButton();
    final JRadioButton commercialRadio = new JRadioButton();
    final JPanel evalCard = new JPanel(new BorderLayout(8, 6));
    final JPanel commercialCard = new JPanel(new BorderLayout(8, 8));
    final JPanel commercialControlsPane = new JPanel();
    final JPanel activeLicenseBanner = new JPanel();
    final JTextField keyField = new JTextField(42);
    final JLabel errorLabel = new JLabel(" ");
    final JButton getLicenseButton = new JButton();
    final JButton trialButton = new JButton();
    final JLabel trialStatusLabel = new JLabel();
    final JButton deactivateButton = new JButton();
    final JLabel termsLabel = new JLabel();
    final JButton primaryButton = new JButton();
    final JButton cancelButton = new JButton(bundle.getString("CancelButton"));

    /**
     * Creates a modal {@link LicenseDialog}.
     *
     * @param owner       parent frame (nullable).
     * @param store       license store used to verify and persist changes.
     * @param initial     current resolved license state.
     * @param startupGate true when opened automatically during Studio startup because attestation is required.
     */
    public LicenseDialog(Frame owner, LicenseStore store, LicenseState initial, boolean startupGate) {
        super(owner, bundle.getString("DialogTitle"), true);
        this.store = Objects.requireNonNull(store, "store");
        this.state = Objects.requireNonNull(initial, "initial");
        this.startupGate = startupGate;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 12));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(16, 20, 16, 20));

        ButtonGroup group = new ButtonGroup();
        group.add(evalRadio);
        group.add(commercialRadio);

        add(createHeaderPane(), BorderLayout.NORTH);
        add(createCardsPane(), BorderLayout.CENTER);
        add(createFooterPane(), BorderLayout.SOUTH);

        // Select initial radio card based on current state
        boolean selectCommercial = initial.mode() == UsageMode.COMMERCIAL_LICENSED
                || initial.mode() == UsageMode.COMMERCIAL_TRIAL
                || initial.warningMessage().isPresent();
        if (selectCommercial) {
            commercialRadio.setSelected(true);
        } else {
            evalRadio.setSelected(true);
        }

        evalRadio.addActionListener(e -> updateSelectionState());
        commercialRadio.addActionListener(e -> {
            updateSelectionState();
            keyField.requestFocusInWindow();
        });

        keyField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onKeyFieldChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onKeyFieldChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onKeyFieldChanged();
            }
        });

        updateSelectionState();
        pack();
        int width = Math.max(680, getPreferredSize().width);
        setSize(width, getPreferredSize().height);
        validate();
        Dimension finalSize = new Dimension(width, getPreferredSize().height);
        setSize(finalSize);
        setMinimumSize(finalSize);
        setLocationRelativeTo(owner);
    }

    /**
     * Returns the resulting {@link LicenseState} after the dialog closes.
     *
     * @return the active {@link LicenseState}.
     */
    public LicenseState resultState() {
        return state;
    }

    private JPanel createHeaderPane() {
        JPanel headerPane = new JPanel();
        headerPane.setLayout(new BoxLayout(headerPane, BoxLayout.Y_AXIS));

        if (state.warningMessage().isPresent()) {
            String warnCode = state.warningMessage().get();
            String warnText = formatWarningBanner(warnCode);
            JLabel warnBanner = new JLabel(warnText);
            warnBanner.setOpaque(true);
            warnBanner.setBackground(new Color(0xFE, 0xF3, 0xC7));
            warnBanner.setForeground(new Color(0x92, 0x40, 0x0E));
            warnBanner.setBorder(new EmptyBorder(8, 10, 8, 10));
            warnBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
            headerPane.add(warnBanner);
            headerPane.add(Box.createVerticalStrut(10));
        }

        JLabel headerLabel = new JLabel(bundle.getString("DialogHeader"));
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, headerLabel.getFont().getSize2D() + 1f));
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPane.add(headerLabel);
        return headerPane;
    }

    private String formatWarningBanner(String warnCode) {
        if (warnCode.startsWith("TrialExpired:")) {
            String expDate = warnCode.substring("TrialExpired:".length());
            return MessageFormat.format(bundle.getString("TrialExpiredStatus"), expDate);
        }
        if (warnCode.startsWith("KeyExpired:")) {
            String expDate = warnCode.substring("KeyExpired:".length());
            return MessageFormat.format(bundle.getString("ErrorExpiredKey"), expDate);
        }
        return bundle.getString("ErrorMalformedKey");
    }

    private JPanel createCardsPane() {
        JPanel cards = new JPanel(new BorderLayout(0, 12));

        // Card 1: Evaluation Mode (BSL 1.1)
        evalRadio.setText(bundle.getString("EvalCardTitle"));
        evalRadio.setFont(evalRadio.getFont().deriveFont(Font.BOLD));
        evalRadio.setOpaque(false);

        JTextArea evalBody = createReadOnlyText(bundle.getString("EvalCardBody"));
        evalBody.setBorder(new EmptyBorder(0, 24, 0, 4));

        evalCard.add(evalRadio, BorderLayout.NORTH);
        evalCard.add(evalBody, BorderLayout.CENTER);
        makeCardClickable(evalCard, evalRadio);

        // Card 2: Commercial Production Use
        commercialRadio.setText(bundle.getString("CommercialCardTitle"));
        commercialRadio.setFont(commercialRadio.getFont().deriveFont(Font.BOLD));
        commercialRadio.setOpaque(false);

        JPanel commercialCenter = new JPanel();
        commercialCenter.setOpaque(false);
        commercialCenter.setLayout(new BoxLayout(commercialCenter, BoxLayout.Y_AXIS));
        commercialCenter.setBorder(new EmptyBorder(0, 24, 0, 4));

        JTextArea commercialBody = createReadOnlyText(bundle.getString("CommercialCardBody"));
        commercialBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        commercialCenter.add(commercialBody);
        commercialCenter.add(Box.createVerticalStrut(10));

        buildActiveLicenseBanner();
        activeLicenseBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        commercialCenter.add(activeLicenseBanner);

        buildCommercialControlsPane();
        commercialControlsPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        commercialCenter.add(commercialControlsPane);

        commercialCard.add(commercialRadio, BorderLayout.NORTH);
        commercialCard.add(commercialCenter, BorderLayout.CENTER);
        makeCardClickable(commercialCard, commercialRadio);

        cards.add(evalCard, BorderLayout.NORTH);
        cards.add(commercialCard, BorderLayout.CENTER);
        return cards;
    }

    private void buildActiveLicenseBanner() {
        activeLicenseBanner.removeAll();
        activeLicenseBanner.setLayout(new BoxLayout(activeLicenseBanner, BoxLayout.Y_AXIS));
        activeLicenseBanner.setBorder(new CompoundBorder(
                new FlatLineBorder(new Insets(8, 10, 8, 10), new Color(0x10, 0xB9, 0x81), 1f, 8),
                new EmptyBorder(2, 2, 2, 2)));
        activeLicenseBanner.setOpaque(false);

        LocalDate today = LocalDate.now();
        if (state.mode() == UsageMode.COMMERCIAL_LICENSED && state.key().isPresent() && !state.key().get().isExpired(today)) {
            LicenseKey k = state.key().get();
            JLabel title = new JLabel(bundle.getString("ActiveLicenseHeader"));
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            activeLicenseBanner.add(title);
            activeLicenseBanner.add(Box.createVerticalStrut(4));

            JLabel customerLbl = new JLabel(MessageFormat.format(bundle.getString("ActiveCustomer"), k.customer()));
            customerLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            activeLicenseBanner.add(customerLbl);

            JLabel tierLbl = new JLabel(MessageFormat.format(
                    bundle.getString("ActiveTierSeats"), k.tier().name(), k.seats()));
            tierLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            activeLicenseBanner.add(tierLbl);

            JLabel expLbl = new JLabel(MessageFormat.format(
                    bundle.getString("ActiveExpires"), k.expiresAt().toString(), k.daysRemaining(today)));
            expLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            activeLicenseBanner.add(expLbl);

            if (state.sourceDescription().isPresent()) {
                JLabel srcLbl = new JLabel(MessageFormat.format(
                        bundle.getString("ActiveSource"), state.sourceDescription().get()));
                srcLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                activeLicenseBanner.add(srcLbl);
            }

            deactivateButton.setText(bundle.getString("DeactivateButton"));
            deactivateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (var al : deactivateButton.getActionListeners()) {
                deactivateButton.removeActionListener(al);
            }
            deactivateButton.addActionListener(e -> {
                state = store.deactivateKey();
                dispose();
            });
            activeLicenseBanner.add(Box.createVerticalStrut(6));
            activeLicenseBanner.add(deactivateButton);
            activeLicenseBanner.add(Box.createVerticalStrut(8));
            activeLicenseBanner.setVisible(true);
        } else {
            activeLicenseBanner.setVisible(false);
        }
    }

    private void buildCommercialControlsPane() {
        commercialControlsPane.setOpaque(false);
        commercialControlsPane.setLayout(new BoxLayout(commercialControlsPane, BoxLayout.Y_AXIS));

        JLabel keyLabel = new JLabel(bundle.getString("LicenseKeyLabel"));
        keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        commercialControlsPane.add(keyLabel);
        commercialControlsPane.add(Box.createVerticalStrut(4));

        keyField.setFont(Monospaced.getFont());
        keyField.putClientProperty(
                FlatClientProperties.PLACEHOLDER_TEXT,
                bundle.getString("LicenseKeyPlaceholder"));
        keyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, keyField.getPreferredSize().height + 4));
        keyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        commercialControlsPane.add(keyField);

        errorLabel.setForeground(new Color(0xDC, 0x26, 0x26));
        errorLabel.setFont(errorLabel.getFont().deriveFont(errorLabel.getFont().getSize2D() - 1f));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        commercialControlsPane.add(Box.createVerticalStrut(2));
        commercialControlsPane.add(errorLabel);
        commercialControlsPane.add(Box.createVerticalStrut(6));

        JPanel actionsRow = new JPanel();
        actionsRow.setLayout(new BoxLayout(actionsRow, BoxLayout.X_AXIS));
        actionsRow.setOpaque(false);
        actionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        getLicenseButton.setText(bundle.getString("GetLicenseLink"));
        getLicenseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getLicenseButton.addActionListener(e -> openBrowser(LICENSING_URL));
        actionsRow.add(getLicenseButton);
        actionsRow.add(Box.createHorizontalStrut(8));

        trialButton.setText(bundle.getString("StartTrialButton"));
        trialButton.addActionListener(e -> onStartTrial());
        actionsRow.add(trialButton);

        commercialControlsPane.add(actionsRow);

        LocalDate today = LocalDate.now();
        if (state.mode() == UsageMode.COMMERCIAL_TRIAL && !state.isTrialExpired(today)) {
            long daysLeft = state.trialDaysRemaining(today);
            String expStr = state.trialExpiresAt().map(LocalDate::toString).orElse("");
            trialStatusLabel.setText(MessageFormat.format(bundle.getString("TrialActiveStatus"), daysLeft, expStr));
            trialStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            commercialControlsPane.add(Box.createVerticalStrut(6));
            commercialControlsPane.add(trialStatusLabel);
            trialButton.setVisible(false);
        } else if (state.isTrialExpired(today)) {
            String expStr = state.trialExpiresAt().map(LocalDate::toString).orElse("");
            trialStatusLabel.setText(MessageFormat.format(bundle.getString("TrialExpiredStatus"), expStr));
            trialStatusLabel.setForeground(new Color(0xD9, 0x77, 0x06));
            trialStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            commercialControlsPane.add(Box.createVerticalStrut(6));
            commercialControlsPane.add(trialStatusLabel);
            trialButton.setEnabled(false);
        }
    }

    private JPanel createFooterPane() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));

        termsLabel.setText(formatFooterTermsHtml(bundle.getString("FooterTerms")));
        termsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        termsLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        termsLabel.setToolTipText(LICENSE_URL);
        termsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openBrowser(LICENSE_URL);
            }
        });
        footer.add(termsLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cancelButton.addActionListener(e -> onCancel());
        primaryButton.addActionListener(e -> onPrimaryAction());
        getRootPane().setDefaultButton(primaryButton);

        if (!startupGate) {
            buttons.add(cancelButton);
        }
        buttons.add(primaryButton);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    static String formatFooterTermsHtml(String footerTerms) {
        String escaped = footerTerms
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String linked = escaped.replace(
                "studio/LICENSE",
                "<a href=\"" + LICENSE_URL + "\">studio/LICENSE</a>");
        return "<html>" + linked + "</html>";
    }

    private void updateSelectionState() {
        boolean isEval = evalRadio.isSelected();
        Color activeBorderColor = UIManager.getColor("Component.focusColor");
        if (activeBorderColor == null) {
            activeBorderColor = new Color(0x3B, 0x82, 0xF6);
        }
        Color inactiveBorderColor = UIManager.getColor("Component.borderColor");
        if (inactiveBorderColor == null) {
            inactiveBorderColor = new Color(0x9C, 0xA3, 0xAF);
        }

        evalCard.setBorder(new FlatLineBorder(
                new Insets(12, 14, 12, 14),
                isEval ? activeBorderColor : inactiveBorderColor,
                isEval ? 2f : 1f,
                10));
        commercialCard.setBorder(new FlatLineBorder(
                new Insets(12, 14, 12, 14),
                !isEval ? activeBorderColor : inactiveBorderColor,
                !isEval ? 2f : 1f,
                10));

        setComponentsEnabled(commercialControlsPane, !isEval);
        if (state.isTrialExpired(LocalDate.now())) {
            trialButton.setEnabled(false);
        }

        if (isEval) {
            primaryButton.setText(bundle.getString("ContinueEvalButton"));
            primaryButton.setEnabled(true);
            errorLabel.setText(" ");
        } else {
            primaryButton.setText(bundle.getString("ActivateButton"));
            primaryButton.setEnabled(!keyField.getText().isBlank());
        }
        repaint();
    }

    private void onKeyFieldChanged() {
        if (!commercialRadio.isSelected()) {
            commercialRadio.setSelected(true);
            updateSelectionState();
        } else {
            primaryButton.setEnabled(!keyField.getText().isBlank());
        }
        errorLabel.setText(" ");
    }

    private void onPrimaryAction() {
        if (evalRadio.isSelected()) {
            state = store.selectEvaluationMode();
            dispose();
            return;
        }

        String rawKey = keyField.getText();
        try {
            state = store.activateKey(rawKey);
            dispose();
        } catch (LicenseVerificationException ex) {
            errorLabel.setText(formatVerificationError(ex));
        }
    }

    private void onStartTrial() {
        try {
            state = store.startCommercialTrial();
            dispose();
        } catch (IllegalStateException ex) {
            errorLabel.setText(ex.getMessage());
            trialButton.setEnabled(false);
        }
    }

    private void onCancel() {
        if (startupGate && state.requiresStartupAttestation(LocalDate.now())) {
            // If user closes the first-run dialog without activating a key, default to Evaluation Mode
            state = store.selectEvaluationMode();
        }
        dispose();
    }

    private String formatVerificationError(LicenseVerificationException ex) {
        return switch (ex.reason()) {
            case EMPTY -> bundle.getString("ErrorEmptyKey");
            case MALFORMED -> bundle.getString("ErrorMalformedKey");
            case INVALID_SIGNATURE -> bundle.getString("ErrorInvalidSignature");
            case INVALID_PAYLOAD -> {
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                if (msg.contains("expired on ")) {
                    int idx = msg.indexOf("expired on ") + "expired on ".length();
                    String date = msg.substring(idx).replace(".", "").strip();
                    yield MessageFormat.format(bundle.getString("ErrorExpiredKey"), date);
                }
                yield MessageFormat.format(bundle.getString("ErrorInvalidPayload"), msg);
            }
        };
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ex) {
            logger.warn("Could not open browser for URL: {}", url, ex);
        }
        JOptionPane.showMessageDialog(
                this,
                url,
                bundle.getString("DialogTitle"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static JTextArea createReadOnlyText(String text) {
        JTextArea area = new JTextArea(text) {
            @Override
            public Dimension getPreferredSize() {
                int width = getWidth() > 0 ? getWidth() : DEFAULT_TEXT_WIDTH;
                Insets insets = getInsets();
                int availWidth = Math.max(100, width - insets.left - insets.right);
                View view = getUI() != null ? getUI().getRootView(this) : null;
                if (view != null) {
                    view.setSize(availWidth, Float.MAX_VALUE);
                    int prefHeight = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS))
                            + insets.top + insets.bottom;
                    return new Dimension(width, prefHeight);
                }
                return new Dimension(width, 40);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(200, pref.height);
            }

            @Override
            public Dimension getMaximumSize() {
                Dimension pref = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, pref.height);
            }
        };
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        area.setSize(DEFAULT_TEXT_WIDTH, Short.MAX_VALUE);
        return area;
    }

    private static void makeCardClickable(JComponent card, JRadioButton radio) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!radio.isSelected()) {
                    radio.doClick();
                }
            }
        };
        attachClickAdapter(card, adapter);
    }

    private static void attachClickAdapter(Container container, MouseAdapter adapter) {
        container.addMouseListener(adapter);
        for (Component child : container.getComponents()) {
            if (child instanceof JTextArea || child instanceof JLabel) {
                child.addMouseListener(adapter);
            } else if (child instanceof JPanel panel) {
                attachClickAdapter(panel, adapter);
            }
        }
    }

    private static void setComponentsEnabled(Container container, boolean enabled) {
        container.setEnabled(enabled);
        for (Component child : container.getComponents()) {
            child.setEnabled(enabled);
            if (child instanceof Container nested) {
                setComponentsEnabled(nested, enabled);
            }
        }
    }
}
