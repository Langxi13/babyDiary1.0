package com.langxi.babydiary.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DiaryImageOrderer {

    private DiaryImageOrderer() {
    }

    static List<String> order(Set<String> retainedImagePaths, List<String> newImagePaths, List<String> imageOrder) {
        if (imageOrder == null || imageOrder.isEmpty()) {
            List<String> result = new ArrayList<>(retainedImagePaths);
            result.addAll(newImagePaths);
            return result;
        }

        List<String> result = new ArrayList<>();
        Set<String> usedExisting = new HashSet<>();
        Set<Integer> usedNew = new HashSet<>();

        for (String orderEntry : imageOrder) {
            if (orderEntry == null) {
                continue;
            }
            if (orderEntry.startsWith("existing:")) {
                String retainedPath = orderEntry.substring("existing:".length());
                if (retainedImagePaths.contains(retainedPath) && usedExisting.add(retainedPath)) {
                    result.add(retainedPath);
                }
            } else if (orderEntry.startsWith("new:")) {
                addNewImage(orderEntry, newImagePaths, usedNew, result);
            }
        }

        for (String retainedPath : retainedImagePaths) {
            if (usedExisting.add(retainedPath)) {
                result.add(retainedPath);
            }
        }
        for (int index = 0; index < newImagePaths.size(); index++) {
            if (usedNew.add(index)) {
                result.add(newImagePaths.get(index));
            }
        }
        return result;
    }

    private static void addNewImage(String orderEntry, List<String> newImagePaths,
                                    Set<Integer> usedNew, List<String> result) {
        try {
            int index = Integer.parseInt(orderEntry.substring("new:".length()));
            if (index >= 0 && index < newImagePaths.size() && usedNew.add(index)) {
                result.add(newImagePaths.get(index));
            }
        } catch (NumberFormatException ignored) {
            // Invalid client ordering entries are ignored; missing images are appended below.
        }
    }
}
