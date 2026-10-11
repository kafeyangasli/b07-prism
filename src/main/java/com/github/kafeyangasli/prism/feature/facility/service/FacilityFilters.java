package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityFilterOption;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import java.util.*;

/** Shared selection semantics for the catalogue, recaps, and exports. */
public final class FacilityFilters {
    private FacilityFilters() {}

    public static List<String> normalize(Collection<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull)
                .map(v -> v.strip().toLowerCase(Locale.ROOT)).filter(v -> !v.isEmpty()).distinct().toList();
    }

    public static List<String> combine(Collection<String> values, String legacy) {
        var all = new ArrayList<String>();
        if (values != null) all.addAll(values);
        if (legacy != null) all.add(legacy);
        return normalize(all);
    }

    public static boolean matches(Facility facility, List<String> types, List<String> locations) {
        var type = facility.getFacilityType();
        return (types.isEmpty() || types.contains(type.getCode().toLowerCase(Locale.ROOT))
                || types.contains(type.getName().toLowerCase(Locale.ROOT)))
                && (locations.isEmpty() || locations.stream().anyMatch(value ->
                    facility.getLocation().toLowerCase(Locale.ROOT).contains(value)));
    }

    public static List<FacilityFilterOption> types(Collection<FacilityType> types, List<String> selected) {
        var options = new ArrayList<FacilityFilterOption>();
        var recognized = new HashSet<String>();
        types.stream().sorted(Comparator.comparing(FacilityType::getName, String.CASE_INSENSITIVE_ORDER)).forEach(type -> {
            String code = type.getCode().toLowerCase(Locale.ROOT), name = type.getName().toLowerCase(Locale.ROOT);
            recognized.add(code); recognized.add(name);
            options.add(new FacilityFilterOption(type.getCode(), type.getName(), selected.contains(code) || selected.contains(name)));
        });
        // Preserve bookmarked/custom filters so another submission cannot silently broaden the result.
        selected.stream().filter(v -> !recognized.contains(v)).forEach(v -> options.add(new FacilityFilterOption(v, v, true)));
        return List.copyOf(options);
    }

    public static List<FacilityFilterOption> locations(Collection<Facility> facilities, List<String> selected) {
        var labels = new TreeMap<String, String>();
        for (var facility : facilities) labels.putIfAbsent(facility.getLocation().strip().toLowerCase(Locale.ROOT), facility.getLocation().strip());
        var options = new ArrayList<FacilityFilterOption>();
        labels.forEach((key, label) -> options.add(new FacilityFilterOption(label, label, selected.contains(key))));
        selected.stream().filter(v -> !labels.containsKey(v)).forEach(v -> options.add(new FacilityFilterOption(v, v, true)));
        return List.copyOf(options);
    }
}
