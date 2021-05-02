/*
 * Copyright (C) 2016-2021 David Rubio Escares / Kodehawa
 *
 *  Mantaro is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  Mantaro is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro. If not, see http://www.gnu.org/licenses/
 */

package net.kodehawa.mantarobot.utils.eval;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ResourceList {
    private final Set<String> resources = new HashSet<>();
    private final Map<String, ResourceList> subpackages = new HashMap<>();
    
    private ResourceList() {}
    
    @Nonnull
    @CheckReturnValue
    public Stream<String> stream(@Nonnull String pkg, boolean recursive) {
        return findPackage(pkg).stream(recursive);
    }
    
    @Nonnull
    private Stream<String> stream(boolean recursive) {
        if(!recursive) {
            return resources.stream();
        }
        var s = Stream.<String>empty();
        for(var p : subpackages.values()) {
            s = Stream.concat(s, p.stream(true));
        }
        return Stream.concat(
                stream(false),
                s
        );
    }
    
    @Nonnull
    public ResourceList findPackage(String name) {
        if(name.isEmpty()) return this;
        var parts = name.split("\\.");
        return resolveChild(parts, parts.length);
    }
    
    @Nonnull
    private ResourceList child(String name) {
        return subpackages.computeIfAbsent(name, __ -> new ResourceList());
    }
    
    @Nonnull
    private ResourceList resolveChild(String[] parts, int limit) {
        ResourceList r = this;
        for(int i = 0; i < limit; i++) {
            r = r.child(parts[i]);
        }
        return r;
    }
    
    @Nonnull
    public static ResourceList fromJar(@Nonnull JarFile jf) {
        var root = new ResourceList();
        try(var stream = jf.versionedStream()) {
            for(var it = stream.iterator(); it.hasNext();) {
                var entry = it.next();
                if(entry.isDirectory()) continue;
                var parts = entry.getRealName().split("/");
                root.resolveChild(parts, parts.length - 1).resources.add(entry.getRealName());
            }
        }
        return root;
    }
}
