package team.tetris.application.network;

/** DNS 조회 없이 입력을 검증하고 같은 주소를 정규화한다. */
public final class Ipv4Address {
    private Ipv4Address() {}

    public static String normalize(String input) {
        String[] parts = input.strip().split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("IPv4 주소를 입력하세요");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}") || Integer.parseInt(part) > 255)
                throw new IllegalArgumentException("IPv4 주소를 입력하세요");
            if (!result.isEmpty()) result.append('.');
            result.append(Integer.parseInt(part));
        }
        int first = Integer.parseInt(parts[0]);
        if (first == 0 || first >= 224)
            throw new IllegalArgumentException("접속 가능한 IPv4 주소를 입력하세요");
        return result.toString();
    }
}
