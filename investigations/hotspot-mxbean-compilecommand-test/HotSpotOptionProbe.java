import java.lang.management.ManagementFactory;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;

public final class HotSpotOptionProbe {
    public static void main(String[] args) throws Exception {
        HotSpotDiagnosticMXBean bean =
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);

        for (String name : new String[] {
            "CompileCommand",
            "CompileCommandFile",
            "CompileThreshold"
        }) {
            try {
                VMOption option = bean.getVMOption(name);
                System.out.println(
                    "GET " + name
                    + " value=" + option.getValue()
                    + " writeable=" + option.isWriteable()
                    + " origin=" + option.getOrigin());
                try {
                    String value;
                    if ("CompileCommand".equals(name)) {
                        value = "exclude,java/lang/String.indexOf";
                    } else if ("CompileCommandFile".equals(name)) {
                        value = ".hotspot_compiler";
                    } else {
                        value = "9999";
                    }
                    bean.setVMOption(name, value);
                    System.out.println(
                        "SET " + name + " PASS value="
                        + bean.getVMOption(name).getValue());
                } catch (Throwable t) {
                    System.out.println(
                        "SET " + name + " FAIL "
                        + t.getClass().getName() + ": " + t.getMessage());
                }
            } catch (Throwable t) {
                System.out.println(
                    "GET " + name + " FAIL "
                    + t.getClass().getName() + ": " + t.getMessage());
            }
        }
    }
}