package team.tetris.application.network;

import java.util.ArrayList;
import java.util.List;
import team.tetris.application.port.RecentIpRepository;
import team.tetris.application.port.StorageException;

/** 저장 실패는 연결을 막지 않는다. 성공한 접속만 최신순으로 기록한다. */
public final class RecentIps {
    private final RecentIpRepository repository;
    private List<String> addresses = List.of();
    private String warning = "";

    public RecentIps(RecentIpRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
        try { addresses = List.copyOf(repository.load()); }
        catch (StorageException error) { warning = "최근 IP를 읽지 못했습니다"; }
    }

    public List<String> addresses() { return addresses; }
    public String warning() { return warning; }

    public void connected(String address) {
        var next = new ArrayList<>(addresses);
        String normalized = Ipv4Address.normalize(address);
        next.remove(normalized);
        next.addFirst(normalized);
        addresses = List.copyOf(next.subList(0, Math.min(5, next.size())));
        try { repository.save(addresses); warning = ""; }
        catch (StorageException error) { warning = "최근 IP 저장에 실패했습니다"; }
    }
}
