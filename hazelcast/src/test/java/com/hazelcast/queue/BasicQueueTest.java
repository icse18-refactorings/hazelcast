/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.queue;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import com.hazelcast.instance.StaticNodeFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @ali 2/12/13
 */
@RunWith(com.hazelcast.util.RandomBlockJUnit4ClassRunner.class)
public class BasicQueueTest {

    @BeforeClass
    public static void init() {
//        System.setProperty("hazelcast.test.use.network","true");
    }

    @Before
    @After
    public void cleanup() {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testOfferPoll() throws Exception {
        Config config = new Config();
        final int count = 100;
        final int insCount = 4;
        final String name = "defQueue";
        final HazelcastInstance[] instances = StaticNodeFactory.newInstances(config, insCount);
        final Random rnd = new Random(System.currentTimeMillis());

        for (int i = 0; i < count; i++) {
            int index = rnd.nextInt(insCount);
            IQueue<String> queue = instances[index].getQueue(name);
            queue.offer("item" + i);
        }

        assertEquals(100, instances[0].getQueue(name).size());

        for (int i = 0; i < count; i++) {
            int index = rnd.nextInt(insCount);
            IQueue<String> queue = instances[index].getQueue(name);
            String item = queue.poll();
            assertEquals(item, "item" + i);
        }
        assertEquals(0, instances[0].getQueue(name).size());
        assertNull(instances[0].getQueue(name).poll());

    }

    @Test
    public void testOfferPollWithTimeout() throws Exception {
        final String name = "defQueue";
        Config config = new Config();
        final int count = 100;
        config.getQueueConfig(name).setMaxSize(count);
        final int insCount = 4;
        final HazelcastInstance[] instances = StaticNodeFactory.newInstances(config, insCount);
        final IQueue<String> q = instances[0].getQueue(name);
        final Random rnd = new Random(System.currentTimeMillis());

        for (int i = 0; i < count; i++) {
            int index = rnd.nextInt(insCount);
            IQueue<String> queue = instances[index].getQueue(name);
            queue.offer("item" + i);
        }

        assertFalse(q.offer("rejected", 1, TimeUnit.SECONDS));
        assertEquals("item0", q.poll());
        assertTrue(q.offer("not rejected", 1, TimeUnit.SECONDS));


        new Thread() {
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                q.poll();
            }
        }.start();
        assertTrue(q.offer("not rejected", 5, TimeUnit.SECONDS));

        assertEquals(count, q.size());

        for (int i = 0; i < count; i++) {
            int index = rnd.nextInt(insCount);
            IQueue<String> queue = instances[index].getQueue(name);
            queue.poll();
        }

        assertNull(q.poll(1, TimeUnit.SECONDS));
        assertTrue(q.offer("offered1"));
        assertEquals("offered1", q.poll(1, TimeUnit.SECONDS));


        new Thread() {
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                q.offer("offered2");
            }
        }.start();
        assertEquals("offered2", q.poll(5, TimeUnit.SECONDS));

        assertEquals(0, q.size());
    }

    @Test
    public void removeAndContains() {
        final String name = "defQueue";
        Config config = new Config();
        final int count = 100;
        config.getQueueConfig(name).setMaxSize(count);
        final int insCount = 4;
        final HazelcastInstance[] instances = StaticNodeFactory.newInstances(config, insCount);


        for (int i = 0; i < 10; i++) {
            getQueue(instances, name).offer("item" + i);
        }

        assertTrue(getQueue(instances, name).contains("item4"));
        assertFalse(getQueue(instances, name).contains("item10"));
        assertTrue(getQueue(instances, name).remove("item4"));
        assertFalse(getQueue(instances, name).contains("item4"));
        assertEquals(getQueue(instances, name).size(), 9);

        List<String> list = new ArrayList<String>(3);
        list.add("item1");
        list.add("item2");
        list.add("item3");

        assertTrue(getQueue(instances, name).containsAll(list));
        list.add("item4");
        assertFalse(getQueue(instances, name).containsAll(list));
    }

    @Test
    public void testDrainAndIterator() {
        final String name = "defQueue";
        Config config = new Config();
        final int count = 100;
        config.getQueueConfig(name).setMaxSize(count);
        final int insCount = 4;
        final HazelcastInstance[] instances = StaticNodeFactory.newInstances(config, insCount);

        for (int i = 0; i < 10; i++) {
            getQueue(instances, name).offer("item" + i);
        }
        Iterator iter = getQueue(instances, name).iterator();
        int i = 0;
        while (iter.hasNext()) {
            Object o = iter.next();
            assertEquals(o, "item" + i++);
        }

        Object[] array = getQueue(instances, name).toArray();
        for (i = 0; i < array.length; i++) {
            Object o = array[i];
            assertEquals(o, "item" + i++);
        }

        String[] arr = new String[5];
        IQueue<String> q = getQueue(instances, name);
        arr = q.toArray(arr);
        assertEquals(arr.length, 10);
        for (i = 0; i < arr.length; i++) {
            Object o = arr[i];
            assertEquals(o, "item" + i++);
        }


        List list = new ArrayList(4);
        getQueue(instances, name).drainTo(list, 4);

        assertEquals(list.remove(0), "item0");
        assertEquals(list.remove(0), "item1");
        assertEquals(list.remove(0), "item2");
        assertEquals(list.remove(0), "item3");
        assertEquals(list.size(), 0);

        getQueue(instances, name).drainTo(list);
        assertEquals(list.size(), 6);
        assertEquals(list.remove(0), "item4");

    }

    @Test
    public void testAddRemoveRetainAll(){
        final String name = "defQueue";
        Config config = new Config();
        final int count = 100;
        config.getQueueConfig(name).setMaxSize(count);
        final int insCount = 4;
        final HazelcastInstance[] instances = StaticNodeFactory.newInstances(config, insCount);

        List<String> list = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            list.add("item" + i);
        }

        assertTrue(getQueue(instances, name).addAll(list));
        assertEquals(getQueue(instances, name).size(), 10);

        List<String> arrayList = new ArrayList<String>();


        arrayList.add("item3");
        arrayList.add("item4");
        arrayList.add("item31");
        assertTrue(getQueue(instances, name).retainAll(arrayList));
        assertEquals(getQueue(instances, name).size(), 2);

        arrayList.clear();
        arrayList.add("item31");
        arrayList.add("item34");
        assertFalse(getQueue(instances, name).removeAll(arrayList));

        arrayList.clear();
        arrayList.add("item3");
        arrayList.add("item4");
        arrayList.add("item12");
        assertTrue(getQueue(instances, name).removeAll(arrayList));

        assertEquals(getQueue(instances, name).size(), 0);
    }

    @Test
    public void testListeners(){
        final String name = "defQueue";
        Config config = new Config();
        final int count = 100;
        config.getQueueConfig(name).setMaxSize(count);
        final int insCount = 4;
        final HazelcastInstance[] instances = StaticNodeFactory.newInstances(config, insCount);

        IQueue q = getQueue(instances, name);
        ItemListener listener = new ItemListener() {
            int offer;

            int poll;

            public void itemAdded(ItemEvent item) {
                assertEquals(item.getItem(), "item"+offer++);
            }

            public void itemRemoved(ItemEvent item) {
                assertEquals(item.getItem(), "item"+poll++);
            }
        };
        q.addItemListener(listener, true);

        for (int i = 0; i < 10; i++) {
            getQueue(instances, name).offer("item" + i);
        }
        for (int i = 0; i < 10; i++) {
            getQueue(instances, name).poll();
        }
        q.removeItemListener(listener);
        getQueue(instances, name).offer("item-a");
        getQueue(instances, name).poll();
    }

    private IQueue getQueue(HazelcastInstance[] instances, String name) {
        final Random rnd = new Random(System.currentTimeMillis());
        return instances[rnd.nextInt(instances.length)].getQueue(name);
    }


}
