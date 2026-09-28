package vn.com.truongsonbank.shared.crypto;

import java.lang.reflect.Field;

final class TsbCryptoEntityProcessor {
    private TsbCryptoEntityProcessor() {
    }

    static void encrypt(Object entity, TsbCryptoService cryptoService) {
        forEachEncryptedField(entity, cryptoService, true);
    }

    static void decrypt(Object entity, TsbCryptoService cryptoService) {
        forEachEncryptedField(entity, cryptoService, false);
    }

    static Object fieldValue(Object entity, String fieldName) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access field: " + fieldName, ex);
        }
    }

    static void setFieldValue(Object entity, String fieldName, Object value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access field: " + fieldName, ex);
        }
    }

    private static void forEachEncryptedField(Object entity, TsbCryptoService cryptoService, boolean writeMode) {
        Class<?> type = entity.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                EncryptedField annotation = field.getAnnotation(EncryptedField.class);
                if (annotation != null) {
                    handleField(entity, field, annotation, cryptoService, writeMode);
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void handleField(Object entity, Field field, EncryptedField annotation,
                                    TsbCryptoService cryptoService, boolean writeMode) {
        if (field.getType() != String.class) {
            throw new IllegalStateException("@EncryptedField only supports String fields: " + field.getName());
        }
        try {
            field.setAccessible(true);
            String value = (String) field.get(entity);
            if (writeMode) {
                if (annotation.searchable() && value != null && !cryptoService.isEncrypted(value)) {
                    setHashField(entity, annotation.hashField(), cryptoService.hashForLookup(value));
                }
                field.set(entity, cryptoService.encrypt(value));
            } else {
                field.set(entity, cryptoService.decrypt(value));
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not access encrypted field: " + field.getName(), ex);
        }
    }

    private static void setHashField(Object entity, String hashFieldName, String hashValue) throws IllegalAccessException {
        if (hashFieldName == null || hashFieldName.isBlank()) {
            throw new IllegalStateException("searchable @EncryptedField requires hashField");
        }
        Field hashField = findField(entity.getClass(), hashFieldName);
        if (hashField.getType() != String.class) {
            throw new IllegalStateException("hashField must be String: " + hashFieldName);
        }
        hashField.setAccessible(true);
        hashField.set(entity, hashValue);
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("field not found: " + fieldName);
    }
}
