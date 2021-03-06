package de.fearnixx.jeak.service.locale;

import de.mlessmann.confort.api.IConfigNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of the locale context.
 * {@inheritDoc}
 */
public class LocaleContext implements ILocaleContext {

    private static final Logger logger = LoggerFactory.getLogger(LocaleContext.class);
    private static final String MISSING_TPL_MESSAGE = "Missing message template %s in localization unit: %s/%s";

    private final String unitId;
    private final Locale locale;
    private final IConfigNode languageNode;
    private final Map<String, MessageRep> preExploded = new HashMap<>();

    public LocaleContext(String unitId, Locale locale, IConfigNode languageNode) {
        this.unitId = unitId;
        this.locale = locale;
        this.languageNode = languageNode;
        preExplodeMessages();
    }

    private void preExplodeMessages() {
        if (!languageNode.isVirtual()) {
            synchronized (preExploded) {
                languageNode.optMap()
                        .orElseThrow(() -> new IllegalStateException("Language context nodes may only be maps!"))
                        .forEach((msgId, template) -> {
                            String templateString = template.optString()
                                    .orElseThrow(() -> new IllegalStateException("Message templates may only be strings!"));

                            logger.debug("[{}] Exploding message: {}.{}", unitId, locale, msgId);
                            preExploded.put(msgId, new MessageRep(templateString));
                        });
            }
        }
    }

    @Override
    public String getUnitId() {
        return unitId;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessage(String messageId, Map<String, String> messageParams) {
        return optMessage(messageId, messageParams)
                .orElseThrow(() -> new IllegalStateException(String.format(MISSING_TPL_MESSAGE, messageId, getUnitId(), getLocale())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessage(String messageId) {
        return optMessage(messageId)
                .orElseThrow(() -> new IllegalStateException(String.format(MISSING_TPL_MESSAGE, messageId, getUnitId(), getLocale())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> optMessage(String messageId, Map<String, String> messageParams) {
        return Optional.ofNullable(uncheckedGetMessage(messageId, messageParams));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> optMessage(String messageId) {
        return Optional.ofNullable(uncheckedGetMessage(messageId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uncheckedGetMessage(String messageId, Map<String, String> messageParams) {
        MessageRep message;
        synchronized (preExploded) {
            message = preExploded.getOrDefault(messageId, null);
        }

        if (message != null) {
            try {
                return message.getWithParams(messageParams);
            } catch (MissingParameterException e) {
                logger.error("Cannot build message \"{}\" with missing parameter: {}", messageId, e.getParameterName());
                return message.getRawTemplate();
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uncheckedGetMessage(String messageId) {
        MessageRep message;
        synchronized (preExploded) {
            message = preExploded.getOrDefault(messageId, null);
        }
        return message != null ? message.getRawTemplate() : null;
    }
}
