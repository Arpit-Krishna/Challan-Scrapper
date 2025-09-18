package org.challan.challan_scraper.utills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public class MapperUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String convertObjectToString(Object object) {
        if (object == null) return null;
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception exception) {
//            ElasticSearchUtils.pushException(exception, "MAPPER_UTILS_EXCEPTION_OBJECT_TO_STRING", ElasticSearchUtils.FATALITY.FATAL);
        }
        return null;
    }

    public static <T> T convertStringToResponseObject(String response, Class<?> claz, Class<?> contentClaz) throws JsonProcessingException {
        if (StringUtils.isEmpty(response)) return null;
        JavaType type = objectMapper.getTypeFactory().constructParametricType(claz, contentClaz);
        return objectMapper.readValue(response, type);
    }

    public static <T> T convertStringToResponseObject(String response, Class<T> claz) throws JsonProcessingException {
        return objectMapper.readValue(response, claz);
    }

    public static <T> T convertObject(Object object, Class<T> claz) {
        return objectMapper.convertValue(object, claz);
    }

    public static <T> T convertObject(Object object, TypeReference<T> typeReference) {
        if (ObjectUtils.isEmpty(object)) return null;
        try {
            return objectMapper.convertValue(object, typeReference);
        } catch (Exception exception) {
//            ElasticSearchUtils.pushException(exception, "MAPPER_UTILS_EXCEPTION_OBJECT_TO_TYPEREF_OBJECT", ElasticSearchUtils.FATALITY.FATAL)
        }
        return null;
    }

}