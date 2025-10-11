import java.util.List;

public class TestParser {
    public static void main(String[] args) {
        // Test the BuildFileParser
        List<String> envVars = com.nms.support.nms_support.service.globalPack.BuildFileParser.parseEnvironmentVariables(".");
        System.out.println("Found environment variables: " + envVars);
    }
}
