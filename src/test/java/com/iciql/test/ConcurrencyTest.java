/*
 * Copyright 2011 James Moger.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iciql.test;

import com.iciql.Db;
import com.iciql.IciqlException;
import com.iciql.Query;
import com.iciql.test.models.Product;
import com.iciql.util.Utils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests concurrency and alias instance sharing.
 */
public class ConcurrencyTest {

    private int numberOfTests = 800;

    @Before
    public void setUp() {
        Db db = IciqlSuite.openNewDb();
        db.insertAll(Product.getList());
    }

    @Test
    public void testAliasSharing() throws Exception {
        Db db = IciqlSuite.openCurrentDb();
        try {
            // Single-threaded example of why aliases can NOT be shared.
            Product p = new Product();
            Query<Product> query1 = db.from(p);
            Query<Product> query2 = db.from(p);

            // if you could share alias instances both counts should be equal
            long count1 = 0;
            try {
                count1 = query1.where(p.category).is("Beverages").selectCount();
            } catch (IciqlException e) {
                assertEquals(IciqlException.CODE_UNMAPPED_FIELD, e.getIciqlCode());
            }
            long count2 = query2.where(p.category).is("Beverages").selectCount();

            // but they aren't
            assertEquals(0, count1);
            assertEquals(2, count2);
            assertTrue(count1 != count2);
        } finally {
            db.close();
        }
    }

    @Test
    @Ignore
    public void testConcurrencyFinal() throws Exception {
        // Multi-threaded example of why aliases can NOT be shared.
        //
        // This test looks like it _could_ work and you may find that it _can_
        // work, but you should also find that it _will_ fail.

        List<Thread> threads = Utils.newArrayList();
        final AtomicInteger failures = new AtomicInteger(0);
        final Product p = new Product();
        for (int i = 0; i < numberOfTests; i++) {
            final int testNumber = i;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        int testCase = testNumber % 10;
                        test(testCase, p);
                    } catch (AssertionError e) {
                        failures.incrementAndGet();
                    } catch (IciqlException e) {
                        failures.incrementAndGet();
                        if (e.getIciqlCode() != IciqlException.CODE_UNMAPPED_FIELD) {
                            System.err.println("UNEXPECTED ERROR in testConcurrencyFinal()");
                            e.printStackTrace();
                        }
                    }
                }
            }, "ICIQL-" + i);
            t.start();
            threads.add(t);
        }

        // wait till all threads complete
        for (Thread t : threads) {
            t.join();
        }

        assertTrue("This should fail. Try running a few more times.", failures.get() > 0);
    }

    @Test
    @Ignore
    public void testConcurrencyThreadLocal() throws Exception {
        List<Thread> threads = Utils.newArrayList();
        final AtomicInteger failures = new AtomicInteger(0);
        final ThreadLocal<Product> tl = Utils.newThreadLocal(Product.class);
        for (int i = 0; i < numberOfTests; i++) {
            final int testNumber = i;
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        int testCase = testNumber % 10;
                        test(testCase, tl.get());
                    } catch (AssertionError e) {
                        failures.incrementAndGet();
                    } catch (IciqlException e) {
                        failures.incrementAndGet();
                        if (e.getIciqlCode() != IciqlException.CODE_UNMAPPED_FIELD) {
                            System.err.println("UNEXPECTED ERROR in testConcurrencyThreadLocal()");
                            e.printStackTrace();
                        }
                    }
                }
            }, "ICIQL-" + i);
            t.start();
            threads.add(t);
        }

        // wait till all threads complete
        for (Thread t : threads) {
            t.join();
        }

        assertEquals("ThreadLocal should never fail!", 0, failures.get());
    }

    private void test(int testCase, Product p) throws AssertionError {
        Db db = IciqlSuite.openCurrentDb();
        try {
            List<Product> list;
            switch (testCase) {
                case 0:
                    list = db.from(p).where(p.productName).is("Chai").select();
                    assertEquals(1, list.size());
                    assertEquals("Chai", list.get(0).productName);
                    break;
                case 1:
                    list = db.from(p).where(p.category).is("Condiments").select();
                    assertEquals(5, list.size());
                    break;
                case 3:
                    list = db.from(p).where(p.productName).is("Aniseed Syrup").select();
                    assertEquals(1, list.size());
                    assertEquals("Aniseed Syrup", list.get(0).productName);
                    break;
                case 4:
                    list = db.from(p).where(p.productName).like("Chef%").select();
                    assertEquals(2, list.size());
                    assertTrue(list.get(0).productName.startsWith("Chef"));
                    assertTrue(list.get(1).productName.startsWith("Chef"));
                    break;
                case 6:
                    list = db.from(p).where(p.unitsInStock).exceeds(0).select();
                    assertEquals(9, list.size());
                    break;
                case 7:
                    list = db.from(p).where(p.unitsInStock).is(0).select();
                    assertEquals(1, list.size());
                    assertEquals("Chef Anton's Gumbo Mix", list.get(0).productName);
                    break;
                case 9:
                    list = db.from(p).where(p.productId).is(7).select();
                    assertEquals(1, list.size());
                    assertTrue(7 == list.get(0).productId);
                    break;
                default:
                    list = db.from(p).select();
                    assertEquals(10, list.size());
            }
        } finally {
            db.close();
        }
    }
}
