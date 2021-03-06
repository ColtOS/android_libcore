/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.java.net;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import libcore.util.SerializationTester;

public class InetAddressTest extends junit.framework.TestCase {
    private static final byte[] LOOPBACK4_BYTES = new byte[] { 127, 0, 0, 1 };
    private static final byte[] LOOPBACK6_BYTES = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };

    private static final String[] INVALID_IPv4_NUMERIC_ADDRESSES = new String[] {
        // IPv4 addresses may not be surrounded by square brackets.
        "[127.0.0.1]",

        // Trailing dots are not allowed.
        "1.2.3.4.",
        // Nor is any kind of trailing junk.
        "1.2.3.4hello",

        // Out of range.
        "256.2.3.4",
        "1.256.3.4",
        "1.2.256.4",
        "1.2.3.256",

        // Deprecated.
        "1.2.3",
        "1.2",
        "1",
        "1234",
        "0", // Single out the deprecated form of the ANY address.

        // Hex. Not supported by Android but supported by the RI.
        "0x1.0x2.0x3.0x4",
        "0x7f.0x00.0x00.0x01",
        "7f.0.0.1",

        // Octal. Not supported by Android but supported by the RI. In the RI, if any of the numbers
        // cannot be treated as a decimal the entire IP is interpreted differently, leading to
        // "0177.00.00.01" -> 177.0.0.1, but "0177.0x0.00.01" -> 127.0.0.1.
        // Android does not do this.
        "0256.00.00.01", // Historically, this could have been interpreted as 174.0.0.1.

        // Negative numbers.
        "-1.0.0.1",
        "1.-1.0.1",
        "1.0.-1.1",
        "1.0.0.-1",
    };

    private static Inet6Address loopback6() throws Exception {
        return (Inet6Address) InetAddress.getByAddress(LOOPBACK6_BYTES);
    }

    private static Inet6Address localhost6() throws Exception {
        return (Inet6Address) InetAddress.getByAddress("ip6-localhost", LOOPBACK6_BYTES);
    }

    public void test_parseNumericAddress() throws Exception {
        // Regular IPv4.
        assertEquals("/1.2.3.4", InetAddress.parseNumericAddress("1.2.3.4").toString());
        // Regular IPv6.
        assertEquals("/2001:4860:800d::68", InetAddress.parseNumericAddress("2001:4860:800d::68").toString());
        // Mapped IPv4
        assertEquals("/127.0.0.1", InetAddress.parseNumericAddress("::ffff:127.0.0.1").toString());
        // Optional square brackets around IPv6 addresses, including mapped IPv4.
        assertEquals("/2001:4860:800d::68", InetAddress.parseNumericAddress("[2001:4860:800d::68]").toString());
        assertEquals("/127.0.0.1", InetAddress.parseNumericAddress("[::ffff:127.0.0.1]").toString());

        try {
            InetAddress.parseNumericAddress("example.com"); // Not numeric.
            fail();
        } catch (IllegalArgumentException expected) {
        }

        // Android does not recognize Octal (leading 0) cases: they are treated as decimal.
        assertEquals("/177.0.0.1", InetAddress.parseNumericAddress("0177.00.00.01").toString());

        for (String invalid : INVALID_IPv4_NUMERIC_ADDRESSES) {
            try {
                InetAddress.parseNumericAddress(invalid);
                fail(invalid);
            } catch (IllegalArgumentException expected) {
            }
        }

        // Strange special cases, for compatibility with InetAddress.getByName.
        assertTrue(InetAddress.parseNumericAddress(null).isLoopbackAddress());
        assertTrue(InetAddress.parseNumericAddress("").isLoopbackAddress());
    }

    public void test_isNumeric() throws Exception {
        // IPv4
        assertTrue(InetAddress.isNumeric("1.2.3.4"));
        assertTrue(InetAddress.isNumeric("127.0.0.1"));

        // IPv6
        assertTrue(InetAddress.isNumeric("::1"));
        assertTrue(InetAddress.isNumeric("2001:4860:800d::68"));

        // Mapped IPv4
        assertTrue(InetAddress.isNumeric("::ffff:127.0.0.1"));

        // Optional square brackets around IPv6 addresses, including mapped IPv4.
        assertTrue(InetAddress.isNumeric("[2001:4860:800d::68]"));
        assertTrue(InetAddress.isNumeric("[::ffff:127.0.0.1]"));

        // Negative test
        assertFalse(InetAddress.isNumeric("example.com"));

        // Android does not handle Octal (leading 0) cases: they are treated as decimal.
        assertTrue(InetAddress.isNumeric("0177.00.00.01")); // Interpreted as 177.0.0.1

        for (String invalid : INVALID_IPv4_NUMERIC_ADDRESSES) {
            assertFalse(invalid, InetAddress.isNumeric(invalid));
        }
    }

    public void test_isLinkLocalAddress() throws Exception {
        assertFalse(InetAddress.getByName("127.0.0.1").isLinkLocalAddress());
        assertFalse(InetAddress.getByName("::ffff:127.0.0.1").isLinkLocalAddress());
        assertTrue(InetAddress.getByName("169.254.1.2").isLinkLocalAddress());

        assertFalse(InetAddress.getByName("fec0::").isLinkLocalAddress());
        assertTrue(InetAddress.getByName("fe80::").isLinkLocalAddress());
    }

    public void test_isMCSiteLocalAddress() throws Exception {
        assertFalse(InetAddress.getByName("239.254.255.255").isMCSiteLocal());
        assertTrue(InetAddress.getByName("239.255.0.0").isMCSiteLocal());
        assertTrue(InetAddress.getByName("239.255.255.255").isMCSiteLocal());
        assertFalse(InetAddress.getByName("240.0.0.0").isMCSiteLocal());

        assertFalse(InetAddress.getByName("ff06::").isMCSiteLocal());
        assertTrue(InetAddress.getByName("ff05::").isMCSiteLocal());
        assertTrue(InetAddress.getByName("ff15::").isMCSiteLocal());
    }

    public void test_isReachable() throws Exception {
        // http://code.google.com/p/android/issues/detail?id=20203
        String s = "aced0005737200146a6176612e6e65742e496e6574416464726573732d9b57af"
                + "9fe3ebdb0200034900076164647265737349000666616d696c794c0008686f737"
                + "44e616d657400124c6a6176612f6c616e672f537472696e673b78704a7d9d6300"
                + "00000274000e7777772e676f6f676c652e636f6d";
        InetAddress inetAddress = InetAddress.getByName("www.google.com");
        new SerializationTester<InetAddress>(inetAddress, s) {
            @Override protected void verify(InetAddress deserialized) throws Exception {
                deserialized.isReachable(500);
                for (NetworkInterface nif
                        : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    deserialized.isReachable(nif, 20, 500);
                }
            }
            @Override protected boolean equals(InetAddress a, InetAddress b) {
                return a.getHostName().equals(b.getHostName());
            }
        }.test();
    }

    public void test_isReachable_neverThrows() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("www.google.com");

        final NetworkInterface netIf = NetworkInterface.getByName("dummy0");
        if (netIf == null) {
            System.logI("Skipping test_isReachable_neverThrows because dummy0 isn't available");
            return;
        }

        assertFalse(inetAddress.isReachable(netIf, 256, 500));
    }

    public void test_isSiteLocalAddress() throws Exception {
        assertFalse(InetAddress.getByName("144.32.32.1").isSiteLocalAddress());
        assertTrue(InetAddress.getByName("10.0.0.1").isSiteLocalAddress());
        assertTrue(InetAddress.getByName("172.16.0.1").isSiteLocalAddress());
        assertFalse(InetAddress.getByName("172.32.0.1").isSiteLocalAddress());
        assertTrue(InetAddress.getByName("192.168.0.1").isSiteLocalAddress());

        assertFalse(InetAddress.getByName("fc00::").isSiteLocalAddress());
        assertTrue(InetAddress.getByName("fec0::").isSiteLocalAddress());
    }

    public void test_getByName() throws Exception {
        for (String invalid : INVALID_IPv4_NUMERIC_ADDRESSES) {
            try {
                InetAddress.getByName(invalid);
                fail(invalid);
            } catch (UnknownHostException expected) {
            }
        }
    }

    public void test_getLoopbackAddress() throws Exception {
        assertTrue(InetAddress.getLoopbackAddress().isLoopbackAddress());
    }

    public void test_equals() throws Exception {
        InetAddress addr = InetAddress.getByName("239.191.255.255");
        assertTrue(addr.equals(addr));
        assertTrue(loopback6().equals(localhost6()));
        assertFalse(addr.equals(loopback6()));

        assertTrue(Inet4Address.LOOPBACK.equals(Inet4Address.LOOPBACK));

        // http://b/4328294 - the scope id isn't included when comparing Inet6Address instances.
        byte[] bs = new byte[16];
        assertEquals(Inet6Address.getByAddress("1", bs, 1), Inet6Address.getByAddress("2", bs, 2));
    }

    public void test_getHostAddress() throws Exception {
        assertEquals("::1", localhost6().getHostAddress());
        assertEquals("::1", InetAddress.getByName("::1").getHostAddress());

        assertEquals("127.0.0.1", Inet4Address.LOOPBACK.getHostAddress());

        // IPv4 mapped address
        assertEquals("127.0.0.1", InetAddress.getByName("::ffff:127.0.0.1").getHostAddress());

        InetAddress aAddr = InetAddress.getByName("224.0.0.0");
        assertEquals("224.0.0.0", aAddr.getHostAddress());


        try {
            InetAddress.getByName("1");
            fail();
        } catch (UnknownHostException expected) {
        }

        byte[] bAddr = {
            (byte) 0xFE, (byte) 0x80, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x02, (byte) 0x11, (byte) 0x25, (byte) 0xFF,
            (byte) 0xFE, (byte) 0xF8, (byte) 0x7C, (byte) 0xB2
        };
        aAddr = Inet6Address.getByAddress(bAddr);
        String aString = aAddr.getHostAddress();
        assertTrue(aString.equals("fe80:0:0:0:211:25ff:fef8:7cb2") || aString.equals("fe80::211:25ff:fef8:7cb2"));

        byte[] cAddr = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        aAddr = Inet6Address.getByAddress(cAddr);
        assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", aAddr.getHostAddress());

        byte[] dAddr = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
        };
        aAddr = Inet6Address.getByAddress(dAddr);
        aString = aAddr.getHostAddress();
        assertTrue(aString.equals("0:0:0:0:0:0:0:0") || aString.equals("::"));

        byte[] eAddr = {
            (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
            (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
            (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b,
            (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f
        };
        aAddr = Inet6Address.getByAddress(eAddr);
        assertEquals("1:203:405:607:809:a0b:c0d:e0f", aAddr.getHostAddress());

        byte[] fAddr = {
            (byte) 0x00, (byte) 0x10, (byte) 0x20, (byte) 0x30,
            (byte) 0x40, (byte) 0x50, (byte) 0x60, (byte) 0x70,
            (byte) 0x80, (byte) 0x90, (byte) 0xa0, (byte) 0xb0,
            (byte) 0xc0, (byte) 0xd0, (byte) 0xe0, (byte) 0xf0
        };
        aAddr = Inet6Address.getByAddress(fAddr);
        assertEquals("10:2030:4050:6070:8090:a0b0:c0d0:e0f0", aAddr.getHostAddress());
    }

    public void test_hashCode() throws Exception {
        InetAddress addr1 = InetAddress.getByName("1.0.0.1");
        InetAddress addr2 = InetAddress.getByName("1.0.0.1");
        assertTrue(addr1.hashCode() == addr2.hashCode());

        assertTrue(loopback6().hashCode() == localhost6().hashCode());
    }

    public void test_toString() throws Exception {
        String validIPAddresses[] = {
            "::1.2.3.4", "::", "::", "1::0", "1::", "::1",
            "FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF",
            "FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:255.255.255.255",
            "0:0:0:0:0:0:0:0", "0:0:0:0:0:0:0.0.0.0"
        };

        String [] resultStrings = {
            "/::1.2.3.4", "/::", "/::", "/1::", "/1::", "/::1",
            "/ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "/ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "/::",
            "/::"
        };

        for(int i = 0; i < validIPAddresses.length; i++) {
            InetAddress ia = InetAddress.getByName(validIPAddresses[i]);
            String result = ia.toString();
            assertNotNull(result);
            assertEquals(resultStrings[i], result);
        }
    }

    public void test_getHostNameCaches() throws Exception {
        InetAddress inetAddress = InetAddress.getByAddress(LOOPBACK6_BYTES);

        // There should be no cached name.
        assertEquals("::1", getHostStringWithoutReverseDns(inetAddress));

        // Force the reverse-DNS lookup.
        assertEquals("ip6-localhost", inetAddress.getHostName());

        // The cached name should now be different.
        assertEquals("ip6-localhost", getHostStringWithoutReverseDns(inetAddress));
    }

    public void test_getByAddress_loopbackIpv4() throws Exception {
        InetAddress inetAddress = InetAddress.getByAddress(LOOPBACK4_BYTES);
        assertEquals(LOOPBACK4_BYTES, "localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getByAddress_loopbackIpv6() throws Exception {
        InetAddress inetAddress = InetAddress.getByAddress(LOOPBACK6_BYTES);
        assertEquals(LOOPBACK6_BYTES, "ip6-localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getByName_loopbackIpv4() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("127.0.0.1");
        assertEquals(LOOPBACK4_BYTES, "localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getByName_loopbackIpv6() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("::1");
        assertEquals(LOOPBACK6_BYTES, "ip6-localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getByName_empty() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("");
        assertEquals(LOOPBACK6_BYTES, "ip6-localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getAllByName_localhost() throws Exception {
        InetAddress[] inetAddresses = InetAddress.getAllByName("localhost");
        assertEquals(1, inetAddresses.length);
        InetAddress inetAddress = inetAddresses[0];
        assertEquals(LOOPBACK4_BYTES, "localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getAllByName_ip6_localhost() throws Exception {
        InetAddress[] inetAddresses = InetAddress.getAllByName("ip6-localhost");
        assertEquals(1, inetAddresses.length);
        InetAddress inetAddress = inetAddresses[0];
        assertEquals(LOOPBACK6_BYTES, "ip6-localhost", inetAddress);
        assertTrue(inetAddress.isLoopbackAddress());
    }

    public void test_getByName_v6loopback() throws Exception {
        InetAddress inetAddress = InetAddress.getByName("::1");

        Set<InetAddress> expectedLoopbackAddresses =
                createSet(Inet4Address.LOOPBACK, Inet6Address.LOOPBACK);
        assertTrue(expectedLoopbackAddresses.contains(inetAddress));
    }

    public void test_getByName_cloning() throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(null);
        InetAddress[] addresses2 = InetAddress.getAllByName(null);
        assertNotNull(addresses[0]);
        assertNotNull(addresses[1]);
        assertNotSame(addresses, addresses2);

        // Also assert that changes to the return value do not affect the cache
        // etc. i.e, that we return a copy.
        addresses[0] = null;
        addresses2 = InetAddress.getAllByName(null);
        assertNotNull(addresses2[0]);
        assertNotNull(addresses2[1]);
    }

    public void test_getAllByName_null() throws Exception {
        InetAddress[] inetAddresses = InetAddress.getAllByName(null);
        assertEquals(2, inetAddresses.length);
        Set<InetAddress> expectedLoopbackAddresses =
                createSet(Inet4Address.LOOPBACK, Inet6Address.LOOPBACK);
        assertEquals(expectedLoopbackAddresses, createSet(inetAddresses));
    }

    // http://b/29311351
    public void test_loopbackConstantsPreInitializedNames() {
        // Note: Inet6Address / Inet4Address equals() does not check host name.
        assertEquals("ip6-localhost", getHostStringWithoutReverseDns(Inet6Address.LOOPBACK));
        assertEquals("localhost", getHostStringWithoutReverseDns(Inet4Address.LOOPBACK));
    }

    private static void assertEquals(
        byte[] expectedAddressBytes, String expectedHostname, InetAddress actual) {
        assertArrayEquals(expectedAddressBytes, actual.getAddress());
        assertEquals(expectedHostname, actual.getHostName());

    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertTrue("Expected=" + Arrays.toString(expected) + ", actual=" + Arrays.toString(actual),
                Arrays.equals(expected, actual));
    }

    private static Set<InetAddress> createSet(InetAddress... members) {
        return new HashSet<InetAddress>(Arrays.asList(members));
    }

    private static String getHostStringWithoutReverseDns(InetAddress inetAddress) {
        // The InetAddress API provides no way of avoiding a DNS lookup, but InetSocketAddress
        // does via InetSocketAddress.getHostString().
        InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 9999);
        return inetSocketAddress.getHostString();
    }
}
