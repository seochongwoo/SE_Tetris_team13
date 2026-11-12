package team.tetris.network;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** VPN/가상 어댑터도 숨기지 않고 활성 인터페이스의 IPv4를 모두 제공한다. */
public final class LocalIpv4Addresses {
    private LocalIpv4Addresses() {}

    public static List<String> discover() {
        var result = new ArrayList<String>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) for (var adapter : Collections.list(interfaces)) {
                if (!adapter.isUp() || adapter.isLoopback()) continue;
                for (var address : Collections.list(adapter.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress())
                        result.add(address.getHostAddress());
                }
            }
        } catch (SocketException ignored) {
            // UI에서 주소를 확인할 수 없다는 안내와 로컬 테스트 주소를 보여준다.
        }
        return result.stream().distinct().sorted().toList();
    }
}
