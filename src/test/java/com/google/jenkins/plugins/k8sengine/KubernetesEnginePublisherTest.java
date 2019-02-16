/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.k8sengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.compute.model.Zone;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.k8sengine.client.ClientFactory;
import com.google.jenkins.plugins.k8sengine.client.CloudResourceManagerClient;
import com.google.jenkins.plugins.k8sengine.client.ComputeClient;
import hudson.AbortException;
import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesEnginePublisherTest {
  private static final String TEST_ZONE_A = "us-west1-a";
  private static final String TEST_ZONE_B = "us-central1-b";
  private static final String TEST_PROJECT_ID = "test-project-id";
  private static final String OTHER_PROJECT_ID = "other-project-id";
  private static final String ERROR_PROJECT_ID = "error-project-id";
  private static final String TEST_CREDENTIALS_ID = "test-credentials-id";
  private static final String EMPTY_PROJECT_CREDENTIALS_ID = "empty-project-credentials-id";
  private static final String PROJECT_ERROR_CREDENTIALS_ID = "project-error-credentials-id";
  private static final String ERROR_CREDENTIALS_ID = "error-credentials-id";
  private static final String TEST_ERROR_MESSAGE = "error";

  private static List<Zone> listOfZones;
  private static List<Project> listOfProjects;
  private static Jenkins jenkins;
  private static KubernetesEnginePublisher.DescriptorImpl descriptor;

  @BeforeClass
  public static void init() throws IOException {
    listOfZones = new ArrayList<>();
    listOfProjects = new ArrayList<>();
    descriptor = Mockito.spy(KubernetesEnginePublisher.DescriptorImpl.class);
    jenkins = Mockito.mock(Jenkins.class);
    ComputeClient computeClient = Mockito.mock(ComputeClient.class);
    Mockito.when(computeClient.getZones(TEST_PROJECT_ID)).thenReturn(listOfZones);
    Mockito.when(computeClient.getZones(ERROR_PROJECT_ID)).thenThrow(new IOException());

    CloudResourceManagerClient cloudResourceManagerClient =
        Mockito.mock(CloudResourceManagerClient.class);
    Mockito.when(cloudResourceManagerClient.getAccountProjects()).thenReturn(listOfProjects);
    ClientFactory clientFactory = Mockito.mock(ClientFactory.class);
    Mockito.when(clientFactory.getDefaultProjectId()).thenReturn(TEST_PROJECT_ID);
    Mockito.when(clientFactory.cloudResourceManagerClient()).thenReturn(cloudResourceManagerClient);
    Mockito.when(clientFactory.computeClient()).thenReturn(computeClient);
    Mockito.doReturn(clientFactory).when(descriptor).getClientFactory(jenkins, TEST_CREDENTIALS_ID);

    ClientFactory emptyProjectClientFactory = Mockito.mock(ClientFactory.class);
    Mockito.when(emptyProjectClientFactory.getDefaultProjectId()).thenReturn("");
    Mockito.when(emptyProjectClientFactory.cloudResourceManagerClient())
        .thenReturn(cloudResourceManagerClient);
    Mockito.doReturn(emptyProjectClientFactory)
        .when(descriptor)
        .getClientFactory(jenkins, EMPTY_PROJECT_CREDENTIALS_ID);

    CloudResourceManagerClient errorCloudResourceManagerClient =
        Mockito.mock(CloudResourceManagerClient.class);
    Mockito.when(errorCloudResourceManagerClient.getAccountProjects()).thenThrow(new IOException());
    ClientFactory projectErrorClientFactory = Mockito.mock(ClientFactory.class);
    Mockito.when(projectErrorClientFactory.getDefaultProjectId()).thenReturn(ERROR_PROJECT_ID);
    Mockito.when(projectErrorClientFactory.cloudResourceManagerClient())
        .thenReturn(errorCloudResourceManagerClient);
    Mockito.doReturn(projectErrorClientFactory)
        .when(descriptor)
        .getClientFactory(jenkins, PROJECT_ERROR_CREDENTIALS_ID);

    Mockito.doThrow(new AbortException(TEST_ERROR_MESSAGE))
        .when(descriptor)
        .getClientFactory(jenkins, ERROR_CREDENTIALS_ID);
  }

  @Before
  public void before() {
    listOfZones.clear();
    listOfProjects.clear();
  }

  @Test
  public void testDoAutoCompleteProjectIdEmptyWithAbortException() {
    testProjectAutoComplete(
        "", ERROR_CREDENTIALS_ID, ImmutableList.of(TEST_PROJECT_ID), ImmutableList.of());
  }

  @Test
  public void testDoAutoCompleteProjectIdDefaultProjectIdWithIOException() {
    testProjectAutoComplete(
        "", PROJECT_ERROR_CREDENTIALS_ID, ImmutableList.of(), ImmutableList.of(ERROR_PROJECT_ID));
  }

  @Test
  public void testDoAutoCompleteProjectIdEmptyWithEmptyCredentialsId() {
    testProjectAutoComplete("", null, ImmutableList.of(TEST_PROJECT_ID), ImmutableList.of());
  }

  @Test
  public void testDoAutoCompleteProjectIdDefaultOnlyWithValidCredentialsIdNoProjects() {
    testProjectAutoComplete(
        "", TEST_CREDENTIALS_ID, ImmutableList.of(), ImmutableList.of(TEST_PROJECT_ID));
  }

  @Test
  public void testDoAutoCompleteProjectIdWithValidCredentialsId() {
    testProjectAutoComplete(
        "",
        TEST_CREDENTIALS_ID,
        ImmutableList.of(OTHER_PROJECT_ID, TEST_PROJECT_ID),
        ImmutableList.of(TEST_PROJECT_ID, OTHER_PROJECT_ID));
  }

  @Test
  public void testDoAutoCompleteProjectIdWithValidCredentialsIdNoDefaultProject() {
    testProjectAutoComplete(
        "",
        TEST_CREDENTIALS_ID,
        ImmutableList.of(OTHER_PROJECT_ID),
        ImmutableList.of(OTHER_PROJECT_ID, TEST_PROJECT_ID));
  }

  @Test
  public void testDoAutoCompleteProjectIdWithValidCredentialsAndEmptyProject() {
    testProjectAutoComplete(
        "",
        EMPTY_PROJECT_CREDENTIALS_ID,
        ImmutableList.of(OTHER_PROJECT_ID, TEST_PROJECT_ID),
        ImmutableList.of(OTHER_PROJECT_ID, TEST_PROJECT_ID));
  }

  @Test
  public void testDoCheckProjectIdMessageWithEmptyProjectID() {
    FormValidation result = descriptor.doCheckProjectId(jenkins, null, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(Messages.KubernetesEnginePublisher_ProjectIDRequired(), result.getMessage());
  }

  @Test
  public void testDoCheckProjectIdMessageWithEmptyCredentialsID() {
    FormValidation result = descriptor.doCheckProjectId(jenkins, TEST_PROJECT_ID, null);
    assertNotNull(result);
    assertEquals(
        Messages.KubernetesEnginePublisher_ProjectCredentialIDRequired(), result.getMessage());
  }

  @Test
  public void testDoCheckProjectIdMessageWithAbortException() {
    FormValidation result =
        descriptor.doCheckProjectId(jenkins, TEST_PROJECT_ID, ERROR_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(Messages.KubernetesEnginePublisher_CredentialAuthFailed(), result.getMessage());
  }

  @Test
  public void testDoCheckProjectIdMessageWithIOException() {
    FormValidation result =
        descriptor.doCheckProjectId(jenkins, TEST_PROJECT_ID, PROJECT_ERROR_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(
        Messages.KubernetesEnginePublisher_ProjectIDVerificationError(), result.getMessage());
  }

  @Test
  public void testDoCheckProjectIdMessageWithNoProjects() {
    FormValidation result =
        descriptor.doCheckProjectId(jenkins, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(
        Messages.KubernetesEnginePublisher_ProjectIDNotUnderCredential(), result.getMessage());
  }

  @Test
  public void testDoCheckProjectIdMessageWithWrongProjects() {
    initProjects(ImmutableList.of(OTHER_PROJECT_ID));
    FormValidation result =
        descriptor.doCheckProjectId(jenkins, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertEquals(
        Messages.KubernetesEnginePublisher_ProjectIDNotUnderCredential(), result.getMessage());
  }

  @Test
  public void testDoCheckProjectIdMessageWithValidProject() {
    initProjects(ImmutableList.of(TEST_PROJECT_ID));
    FormValidation result =
        descriptor.doCheckProjectId(jenkins, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertEquals(FormValidation.ok().getMessage(), result.getMessage());
  }

  @Test
  public void testDoAutoCompleteZoneEmptyWithEmptyProjectId() {
    testZoneAutoComplete(
        "", null, TEST_CREDENTIALS_ID, ImmutableList.of(TEST_ZONE_A), ImmutableList.of());
  }

  @Test
  public void testDoAutoCompleteZoneEmptyWithEmptyCredentialsId() {
    testZoneAutoComplete(
        "", TEST_PROJECT_ID, null, ImmutableList.of(TEST_ZONE_A), ImmutableList.of());
  }

  @Test
  public void testDoAutoCompleteZoneWithValidArguments() {
    testZoneAutoComplete(
        "",
        TEST_PROJECT_ID,
        TEST_CREDENTIALS_ID,
        ImmutableList.of(TEST_ZONE_A, TEST_ZONE_B),
        ImmutableList.of(TEST_ZONE_A, TEST_ZONE_B));
  }

  @Test
  public void testDoAutoCompleteZoneEmptyWithValidArgumentsNoZones() {
    testZoneAutoComplete(
        "", TEST_PROJECT_ID, TEST_CREDENTIALS_ID, ImmutableList.of(), ImmutableList.of());
  }

  @Test
  public void testDoAutoCompleteZoneEmptyWithIOException() {
    testZoneAutoComplete(
        "", ERROR_PROJECT_ID, TEST_CREDENTIALS_ID, ImmutableList.of(), ImmutableList.of());
  }

  @Test
  public void testDoCheckZoneMessageWithEmptyZone() {
    FormValidation result =
        descriptor.doCheckZone(jenkins, null, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(Messages.KubernetesEnginePublisher_ZoneRequired(), result.getMessage());
  }

  @Test
  public void testDoCheckZoneMessageWithEmptyProjectId() {
    FormValidation result = descriptor.doCheckZone(jenkins, TEST_ZONE_A, null, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(
        Messages.KubernetesEnginePublisher_ZoneProjectIdCredentialRequired(), result.getMessage());
  }

  @Test
  public void testDoCheckZoneMessageWithEmptyCredentialsId() {
    FormValidation result = descriptor.doCheckZone(jenkins, TEST_ZONE_A, TEST_PROJECT_ID, null);
    assertNotNull(result);
    assertEquals(
        Messages.KubernetesEnginePublisher_ZoneProjectIdCredentialRequired(), result.getMessage());
  }

  @Test
  public void testDoCheckZoneMessageWithNoAvailableZones() {
    FormValidation result =
        descriptor.doCheckZone(jenkins, TEST_ZONE_A, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(Messages.KubernetesEnginePublisher_ZoneNotInProject(), result.getMessage());
  }

  @Test
  public void testDoCheckZoneMessageWithNonMatchingZones() {
    initZones(ImmutableList.of(TEST_ZONE_B));
    FormValidation result =
        descriptor.doCheckZone(jenkins, TEST_ZONE_A, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(Messages.KubernetesEnginePublisher_ZoneNotInProject(), result.getMessage());
  }

  @Test
  public void testDoCheckZoneMessageWithIOException() {
    FormValidation result =
        descriptor.doCheckZone(jenkins, TEST_ZONE_A, ERROR_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(Messages.KubernetesEnginePublisher_ZoneVerificationError(), result.getMessage());
  }

  @Test
  public void testDoCheckZoneOKWithMatchingZones() {
    initZones(ImmutableList.of(TEST_ZONE_A));
    FormValidation result =
        descriptor.doCheckZone(jenkins, TEST_ZONE_A, TEST_PROJECT_ID, TEST_CREDENTIALS_ID);
    assertNotNull(result);
    assertEquals(FormValidation.ok(), result);
  }

  private static void initZones(List<String> zoneNames) {
    zoneNames.forEach(z -> listOfZones.add(new Zone().setName(z)));
  }

  private static void initProjects(List<String> projectNames) {
    projectNames.forEach(p -> listOfProjects.add(new Project().setProjectId(p)));
  }

  private static void testZoneAutoComplete(
      String zone,
      String projectId,
      String credentialsId,
      List<String> init,
      List<String> expected) {
    initZones(init);
    testAutoCompleteResult(
        expected, descriptor.doAutoCompleteZone(jenkins, zone, projectId, credentialsId));
  }

  private static void testProjectAutoComplete(
      String projectId, String credentialsId, List<String> init, List<String> expected) {
    initProjects(init);
    testAutoCompleteResult(
        expected, descriptor.doAutoCompleteProjectId(jenkins, projectId, credentialsId));
  }

  private static void testAutoCompleteResult(
      List<String> expected, AutoCompletionCandidates items) {
    assertNotNull(items);
    List<String> values = items.getValues();
    assertNotNull(values);
    assertEquals(expected, values);
  }
}
