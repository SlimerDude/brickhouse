/* 
 * Copyright (c) 2015, Matthew Lohbihler
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.brickhouse.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableInt;
import org.brickhouse.CancelReadException;
import org.brickhouse.Table;
import org.brickhouse.TableListener;
import org.brickhouse.datatype.HMap;
import org.brickhouse.datatype.HReference;
import org.brickhouse.datatype.HValue;
import org.brickhouse.filter.Filter;
import org.brickhouse.filter.Filter.Pather;

public class MemoryTable2 implements Table {
    final Map<String, HMap> data = new ConcurrentHashMap<>();
    private final boolean setDis;

    public MemoryTable2(boolean setDis) {
        this.setDis = setDis;
    }

    @Override
    public HMap read(Filter filter) {
        return read(filter, setDis);
    }

    @Override
    public HMap read(Filter filter, boolean setDis) {
        HMap result = null;

        for (HMap row : data.values()) {
            if (filter.include(row, pather)) {
                result = new HMap(row);
                break;
            }
        }

        if (setDis)
            return fillDii(result);
        return result;
    }

    @Override
    public HMap readById(String id) {
        return readById(id, setDis);
    }

    @Override
    public HMap readById(String id, boolean setDis) {
        HMap result = data.get(id);
        if (result == null)
            return null;
        result = new HMap(result);
        if (setDis)
            return fillDii(result);
        return result;
    }

    @Override
    public HMap readById(HReference id) {
        return readById(id.getId(), setDis);
    }

    @Override
    public HMap readById(HReference id, boolean setDis) {
        return readById(id.getId(), setDis);
    }

    @Override
    public void readAll(Filter filter, Consumer<HMap> consumer) {
        readAll(filter, consumer, setDis);
    }

    @Override
    public void readAll(Filter filter, Consumer<HMap> consumer, boolean fillDii) {
        try {
            for (HMap row : data.values()) {
                if (filter.include(row, pather)) {
                    if (fillDii)
                        fillDii(row);
                    consumer.accept(new HMap(row));
                }
            }
        }
        catch (CancelReadException e) {
            // no op
        }
    }

    @Override
    public List<HMap> readAll(Filter filter) {
        return readAll(filter, setDis);
    }

    @Override
    public List<HMap> readAll(Filter filter, boolean setDis) {
        final List<HMap> list = new ArrayList<>();
        readAll(filter, row -> list.add(row), setDis);
        return list;
    }

    @Override
    public int count(Filter filter) {
        final MutableInt count = new MutableInt();
        readAll(filter, row -> count.increment(), false);
        return count.intValue();
    }

    @Override
    public void insert(HMap record) {
        HReference id = record.id();
        if (id == null)
            throw new RuntimeException("id is required");
        HMap copy = clean(new HMap(record));
        clean(copy);
        data.put(id.getId(), copy);
    }

    @Override
    public void batchInsert(List<HMap> records) {
        for (HMap row : records)
            insert(row);
    }

    @Override
    public int updateById(HMap map) {
        HReference id = map.id();
        if (id == null)
            throw new RuntimeException("id is required");
        if (data.containsKey(id.getId())) {
            HMap copy = clean(new HMap(map));
            data.put(id.getId(), copy);
            return 1;
        }
        return 0;
    }

    @Override
    public int deleteById(String id) {
        if (data.remove(id) != null) {
            return 1;
        }
        return 0;
    }

    @Override
    public int deleteById(HReference id) {
        return deleteById(id.getId());
    }

    @Override
    public int deleteAll(Filter filter) {
        int count = 0;
        for (Entry<String, HMap> e : data.entrySet()) {
            if (filter.include(e.getValue(), pather)) {
                data.remove(e.getKey());
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean include(HMap map, Filter filter) {
        return filter.include(map, pather);
    }

    @Override
    public void close() {
    }

    @Override
    public void addListener(TableListener listener) {
    }

    @Override
    public void removeListener(TableListener listener) {
    }

    //
    // Private(ish)
    //
    Pather pather = new Pather() {
        @Override
        public HMap find(String ref) {
            return data.get(ref);
        }
    };

    HMap fillDii(HMap map) {
        if (map != null) {
            HMap refs = null;
            for (Entry<String, HValue> e : map.entrySet()) {
                /* Don't fill id fields. */
                if (e.getValue() instanceof HReference && !"id".equals(e.getKey())) {
                    HReference r = (HReference) e.getValue();
                    HMap referent = data.get(r.getId());
                    if (referent != null) {
                        String dis = referent.disOrNull();
                        if (dis != null) {
                            if (refs == null)
                                refs = new HMap();
                            refs.put(e.getKey(), new HReference(r.getId(), dis));
                        }
                    }
                }
            }
            ;
            if (refs != null)
                map.merge(refs);
        }
        return map;
    }
}
