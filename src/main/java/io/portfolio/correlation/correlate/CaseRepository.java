package io.portfolio.correlation.correlate;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CaseRepository {

    private final ConcurrentMap<String, CaseRecord> cases = new ConcurrentHashMap<>();

    public CaseRecord save(CaseRecord record) {
        cases.put(record.id(), record);
        return record;
    }

    public Optional<CaseRecord> find(String id) {
        return Optional.ofNullable(cases.get(id));
    }

    /**
     * The open case for a given type and classifier, if there is one.
     *
     * <p>Closed cases are excluded deliberately: once a case is closed, a new alert with the same
     * classifier represents a recurrence, and reopening the old case would hide that it happened
     * twice.
     */
    public Optional<CaseRecord> findOpen(String caseTypeId, String classifier) {
        return cases.values().stream()
                .filter(CaseRecord::isOpen)
                .filter(record -> record.caseTypeId().equals(caseTypeId))
                .filter(record -> record.classifier().equals(classifier))
                .findFirst();
    }

    public List<CaseRecord> openCases() {
        return cases.values().stream()
                .filter(CaseRecord::isOpen)
                .sorted(Comparator.comparing(CaseRecord::openedAt))
                .toList();
    }

    public List<CaseRecord> all() {
        return cases.values().stream()
                .sorted(Comparator.comparing(CaseRecord::openedAt))
                .toList();
    }

    public void clear() {
        cases.clear();
    }
}
