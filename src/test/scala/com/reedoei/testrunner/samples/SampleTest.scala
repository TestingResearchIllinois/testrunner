package com.reedoei.testrunner.samples

import org.junit.{Assert, Ignore, Test}

@Ignore
class SampleTest {
    @Test
    def test1(): Unit = {
        Assert.assertTrue(true)
    }
}
