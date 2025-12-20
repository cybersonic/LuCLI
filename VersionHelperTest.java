import org.lucee.lucli.commands.SimpleServerConfigHelper;
import java.util.List;

public class VersionHelperTest {
    public static void main(String[] args) {
        try {
            SimpleServerConfigHelper helper = new SimpleServerConfigHelper();
            List<String> versions = helper.getAvailableVersions();
            
            System.out.println("Total versions available: " + versions.size());
            
            int count7x = 0;
            for (String version : versions) {
                if (version.startsWith("7")) {
                    System.out.println("Found 7.x version: " + version);
                    count7x++;
                }
            }
            
            if (count7x > 0) {
                System.out.println("SUCCESS: Found " + count7x + " version(s) starting with '7'");
                System.exit(0);
            } else {
                System.out.println("FAILED: No versions starting with '7' found");
                System.out.println("All versions:");
                for (String version : versions) {
                    System.out.println("  " + version);
                }
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
