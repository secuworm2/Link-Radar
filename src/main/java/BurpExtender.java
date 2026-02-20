import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.secuworm.endpointcollector.EndpointCollectorExtension;

public class BurpExtender implements BurpExtension {
    private final EndpointCollectorExtension delegate = new EndpointCollectorExtension();

    @Override
    public void initialize(MontoyaApi api) {
        delegate.initialize(api);
    }
}
