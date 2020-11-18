/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.indexmanagement.rollup.resthandler

import com.amazon.opendistroforelasticsearch.indexmanagement.IndexManagementPlugin.Companion.ROLLUP_JOBS_BASE_URI
import com.amazon.opendistroforelasticsearch.indexmanagement.makeRequest
import com.amazon.opendistroforelasticsearch.indexmanagement.randomInstant
import com.amazon.opendistroforelasticsearch.indexmanagement.rollup.RollupRestTestCase
import com.amazon.opendistroforelasticsearch.indexmanagement.rollup.model.Rollup
import com.amazon.opendistroforelasticsearch.indexmanagement.rollup.model.RollupMetadata
import com.amazon.opendistroforelasticsearch.indexmanagement.rollup.model.dimension.DateHistogram
import com.amazon.opendistroforelasticsearch.indexmanagement.rollup.model.dimension.Terms
import com.amazon.opendistroforelasticsearch.indexmanagement.rollup.randomRollup
import com.amazon.opendistroforelasticsearch.indexmanagement.waitFor
import com.amazon.opendistroforelasticsearch.jobscheduler.spi.schedule.IntervalSchedule
import org.elasticsearch.client.ResponseException
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.test.junit.annotations.TestLogging
import java.time.Instant
import java.time.temporal.ChronoUnit

@TestLogging(value = "level:DEBUG", reason = "Debugging tests")
@Suppress("UNCHECKED_CAST")
class RestStopRollupActionIT : RollupRestTestCase() {

    @Throws(Exception::class)
    fun `test stopping a started rollup`() {
        val rollup = createRollup(randomRollup().copy(enabled = true, jobEnabledTime = randomInstant(), metadataID = null))
        assertTrue("Rollup was not enabled", rollup.enabled)

        val response = client().makeRequest("POST", "$ROLLUP_JOBS_BASE_URI/${rollup.id}/_stop")
        assertEquals("Stop rollup failed", RestStatus.OK, response.restStatus())
        val expectedResponse = mapOf("acknowledged" to true)
        assertEquals(expectedResponse, response.asMap())

        val updatedRollup = getRollup(rollup.id)
        assertFalse("Rollup was not disabled", updatedRollup.enabled)
    }

    @Throws(Exception::class)
    fun `test stopping a stopped rollup`() {
        val rollup = createRollup(randomRollup().copy(enabled = true, jobEnabledTime = randomInstant(), metadataID = null))
        assertTrue("Rollup was not enabled", rollup.enabled)

        val response = client().makeRequest("POST", "$ROLLUP_JOBS_BASE_URI/${rollup.id}/_stop")
        assertEquals("Stop rollup failed", RestStatus.OK, response.restStatus())
        val expectedResponse = mapOf("acknowledged" to true)
        assertEquals(expectedResponse, response.asMap())

        val updatedRollup = getRollup(rollup.id)
        assertFalse("Rollup was not disabled", updatedRollup.enabled)

        val secondResponse = client().makeRequest("POST", "$ROLLUP_JOBS_BASE_URI/${rollup.id}/_stop")
        assertEquals("Stop rollup failed", RestStatus.OK, secondResponse.restStatus())
        val expectedSecondResponse = mapOf("acknowledged" to true)
        assertEquals(expectedSecondResponse, secondResponse.asMap())

        val updatedSecondRollup = getRollup(rollup.id)
        assertFalse("Rollup was not disabled", updatedSecondRollup.enabled)
    }

    @Throws(Exception::class)
    fun `test stopping rollup with metadata`() {
        generateNYCTaxiData("source")
        val rollup = Rollup(
            id = "basic_term_query",
            schemaVersion = 1L,
            enabled = true,
            jobSchedule = IntervalSchedule(Instant.now(), 1, ChronoUnit.MINUTES),
            jobLastUpdatedTime = Instant.now(),
            jobEnabledTime = Instant.now(),
            description = "basic search test",
            sourceIndex = "source",
            targetIndex = "target",
            metadataID = null,
            roles = emptyList(),
            pageSize = 10,
            delay = 0,
            continuous = false,
            dimensions = listOf(
                DateHistogram(sourceField = "tpep_pickup_datetime", fixedInterval = "1h"),
                Terms("RatecodeID", "RatecodeID"),
                Terms("PULocationID", "PULocationID")
            ),
            metrics = emptyList()
        ).let { createRollup(it, it.id) }

        updateRollupStartTime(rollup)

        waitFor {
            val rollupJob = getRollup(rollupId = rollup.id)
            assertNotNull("Rollup job doesn't have metadata set", rollupJob.metadataID)
            val rollupMetadata = getRollupMetadata(rollupJob.metadataID!!)
            assertEquals("Rollup is not STARTED", RollupMetadata.Status.STARTED, rollupMetadata.status)
        }

        val response = client().makeRequest("POST", "$ROLLUP_JOBS_BASE_URI/${rollup.id}/_stop")
        assertEquals("Stop rollup failed", RestStatus.OK, response.restStatus())
        val expectedResponse = mapOf("acknowledged" to true)
        assertEquals(expectedResponse, response.asMap())

        val updatedRollup = getRollup(rollup.id)
        assertFalse("Rollup was not disabled", updatedRollup.enabled)
        val rollupMetadata = getRollupMetadata(updatedRollup.metadataID!!)
        assertEquals("Rollup is not STOPPED", RollupMetadata.Status.STOPPED, rollupMetadata.status)
    }

    @Throws(Exception::class)
    fun `test stop a rollup with no id fails`() {
        try {
            client().makeRequest("POST", "$ROLLUP_JOBS_BASE_URI//_stop")
            fail("Expected 400 Method BAD_REQUEST response")
        } catch (e: ResponseException) {
            assertEquals("Unexpected status", RestStatus.BAD_REQUEST, e.response.restStatus())
        }
    }
}