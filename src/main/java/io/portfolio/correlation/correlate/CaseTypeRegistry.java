package io.portfolio.correlation.correlate;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CaseTypeRegistry {

    private final ConcurrentMap<String, CaseType> caseTypes = new ConcurrentHashMap<>();

    public CaseType save(CaseType caseType) {
        caseTypes.put(caseType.id(), caseType);
        return caseType;
    }

    public Optional<CaseType> find(String id) {
        return Optional.ofNullable(caseTypes.get(id));
    }

    public List<CaseType> all() {
        return caseTypes.values().stream()
                .sorted(java.util.Comparator.comparing(CaseType::name))
                .toList();
    }
}
