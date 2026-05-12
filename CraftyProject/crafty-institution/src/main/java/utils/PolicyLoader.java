package utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import institutions_Fuzzy.Policy;

import java.io.File;
import java.util.List;
/*
 * @author yongchao Zeng
 *
 */
public class PolicyLoader {
    public static List<Policy> loadPolicies(String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(new File(filePath), new TypeReference<List<Policy>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
