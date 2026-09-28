package vn.com.truongsonbank.shared.crypto;

import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;

import java.lang.reflect.Field;

final class TsbCryptoHibernateEventListener implements PreInsertEventListener, PreUpdateEventListener, PostLoadEventListener {
    private final TsbCryptoService cryptoService;

    TsbCryptoHibernateEventListener(TsbCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public boolean onPreInsert(PreInsertEvent event) {
        if (!isEncryptedEntity(event.getEntity())) {
            return false;
        }
        encryptState(event.getEntity(), event.getPersister().getPropertyNames(), event.getState());
        return false;
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        if (!isEncryptedEntity(event.getEntity())) {
            return false;
        }
        encryptState(event.getEntity(), event.getPersister().getPropertyNames(), event.getState());
        return false;
    }

    @Override
    public void onPostLoad(PostLoadEvent event) {
        if (isEncryptedEntity(event.getEntity())) {
            TsbCryptoEntityProcessor.decrypt(event.getEntity(), cryptoService);
        }
    }

    private boolean isEncryptedEntity(Object entity) {
        return entity != null && entity.getClass().isAnnotationPresent(EncryptedEntity.class);
    }

    private void encryptState(Object entity, String[] propertyNames, Object[] state) {
        Class<?> type = entity.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                EncryptedField annotation = field.getAnnotation(EncryptedField.class);
                if (annotation != null) {
                    encryptField(entity, field, annotation, propertyNames, state);
                }
            }
            type = type.getSuperclass();
        }
    }

    private void encryptField(Object entity, Field field, EncryptedField annotation, String[] propertyNames, Object[] state) {
        if (field.getType() != String.class) {
            throw new IllegalStateException("@EncryptedField only supports String fields: " + field.getName());
        }
        String value = (String) TsbCryptoEntityProcessor.fieldValue(entity, field.getName());
        setState(propertyNames, state, field.getName(), cryptoService.encrypt(value));
        if (annotation.searchable() && value != null && !cryptoService.isEncrypted(value)) {
            String hash = cryptoService.hashForLookup(value);
            setState(propertyNames, state, annotation.hashField(), hash);
            TsbCryptoEntityProcessor.setFieldValue(entity, annotation.hashField(), hash);
        }
    }

    private void setState(String[] propertyNames, Object[] state, String propertyName, Object value) {
        for (int i = 0; i < propertyNames.length; i++) {
            if (propertyNames[i].equals(propertyName)) {
                state[i] = value;
                return;
            }
        }
        throw new IllegalStateException("Encrypted entity property not found: " + propertyName);
    }
}
