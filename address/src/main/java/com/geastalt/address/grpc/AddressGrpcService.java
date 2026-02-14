/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@geastalt.com for commercial licensing.
 */

package com.geastalt.address.grpc;

import com.geastalt.address.CountryCode;
import com.geastalt.address.format.FormatVerificationResult;
import com.geastalt.address.grpc.generated.*;
import com.geastalt.address.provider.ProviderRegistry;
import com.geastalt.address.provider.VerificationRequest;
import com.geastalt.address.provider.VerificationResult;
import com.geastalt.address.service.AddressFormatService;
import com.geastalt.address.service.AddressVerificationService;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;

@Slf4j
@GrpcService
public class AddressGrpcService extends AddressServiceGrpc.AddressServiceImplBase {

    private final AddressVerificationService verificationService;
    private final AddressFormatService formatService;
    private final ProviderRegistry providerRegistry;
    private final Tracer tracer;
    private final boolean grpcTracingEnabled;

    public AddressGrpcService(final AddressVerificationService verificationService,
                              final AddressFormatService formatService,
                              final ProviderRegistry providerRegistry,
                              final Tracer tracer,
                              @Value("${address.tracing.grpc.enabled:true}") final boolean grpcTracingEnabled) {
        this.verificationService = verificationService;
        this.formatService = formatService;
        this.providerRegistry = providerRegistry;
        this.tracer = tracer;
        this.grpcTracingEnabled = grpcTracingEnabled;
    }

    @Override
    public void verifyAddress(final VerifyAddressRequest request,
                              final StreamObserver<VerifyAddressResponse> responseObserver) {
        final var span = startGrpcSpan("VerifyAddress");
        try (final var scope = span.makeCurrent()) {
            final var addr = request.getAddress();
            final var countryCode = addr.getCountryCode();
            final var providerOverride = request.hasProviderId() ? request.getProviderId() : null;

            final var verificationRequest = VerificationRequest.builder()
                    .countryCode(countryCode)
                    .addressLines(addr.getAddressLinesList())
                    .locality(addr.getLocality())
                    .administrativeArea(addr.getAdministrativeArea())
                    .postalCode(addr.getPostalCode())
                    .subLocality(addr.hasSubLocality() ? addr.getSubLocality() : null)
                    .organization(addr.hasOrganization() ? addr.getOrganization() : null)
                    .recipient(addr.hasRecipient() ? addr.getRecipient() : null)
                    .build();

            final Optional<AddressVerificationService.Result> resultOpt;

            if (providerOverride != null && !providerOverride.isEmpty()) {
                final var provider = providerRegistry.getProviderByOverride(countryCode, providerOverride);
                if (provider.isEmpty()) {
                    responseObserver.onNext(VerifyAddressResponse.newBuilder()
                            .setStatus(VerificationStatus.PROVIDER_UNAVAILABLE)
                            .setMessage("No verification provider available for country: "
                                    + CountryCode.displayName(countryCode))
                            .build());
                    responseObserver.onCompleted();
                    span.setStatus(StatusCode.OK);
                    return;
                }
                resultOpt = Optional.of(verificationService.verify(verificationRequest, provider.get()));
            } else {
                resultOpt = verificationService.verifyWithRouting(verificationRequest);
            }

            if (resultOpt.isEmpty()) {
                responseObserver.onNext(VerifyAddressResponse.newBuilder()
                        .setStatus(VerificationStatus.PROVIDER_UNAVAILABLE)
                        .setMessage("No verification provider available for country: "
                                + CountryCode.displayName(countryCode))
                        .build());
                responseObserver.onCompleted();
                span.setStatus(StatusCode.OK);
                return;
            }

            responseObserver.onNext(buildVerifyResponse(resultOpt.get(), countryCode));
            responseObserver.onCompleted();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage()).asRuntimeException());
        } finally {
            span.end();
        }
    }

    private VerifyAddressResponse buildVerifyResponse(final AddressVerificationService.Result result,
                                                       final int countryCode) {
        final var vr = result.verificationResult();
        final var responseBuilder = VerifyAddressResponse.newBuilder()
                .setStatus(mapVerificationStatus(vr.getStatus()))
                .setProviderId(result.providerId());

        if (vr.getMessage() != null) {
            responseBuilder.setMessage(vr.getMessage());
        }

        if (vr.getMetadata() != null) {
            responseBuilder.putAllMetadata(vr.getMetadata());
        }

        if (vr.getLatitude() != null) {
            responseBuilder.setLatitude(vr.getLatitude());
        }
        if (vr.getLongitude() != null) {
            responseBuilder.setLongitude(vr.getLongitude());
        }

        if (vr.getAddressLines() != null) {
            final var standardized = PostalAddress.newBuilder()
                    .setCountryCode(vr.getCountryCode() != 0 ? vr.getCountryCode() : countryCode)
                    .addAllAddressLines(vr.getAddressLines());
            if (vr.getLocality() != null) standardized.setLocality(vr.getLocality());
            if (vr.getAdministrativeArea() != null) standardized.setAdministrativeArea(vr.getAdministrativeArea());
            if (vr.getPostalCode() != null) standardized.setPostalCode(vr.getPostalCode());
            if (vr.getSubLocality() != null) standardized.setSubLocality(vr.getSubLocality());
            responseBuilder.setStandardizedAddress(standardized.build());
        }

        return responseBuilder.build();
    }

    @Override
    public void verifyAddressFormat(final VerifyAddressFormatRequest request,
                                    final StreamObserver<VerifyAddressFormatResponse> responseObserver) {
        final var span = startGrpcSpan("VerifyAddressFormat");
        try (final var scope = span.makeCurrent()) {
            final var addr = request.getAddress();
            final var countryCode = addr.getCountryCode();

            final var resultOpt = formatService.verify(
                    countryCode,
                    addr.getAddressLinesList(),
                    addr.getLocality(),
                    addr.getAdministrativeArea(),
                    addr.getPostalCode(),
                    addr.hasSubLocality() ? addr.getSubLocality() : null
            );

            if (resultOpt.isEmpty()) {
                responseObserver.onNext(VerifyAddressFormatResponse.newBuilder()
                        .setStatus(FormatStatus.FORMAT_UNSUPPORTED_COUNTRY)
                        .build());
                responseObserver.onCompleted();
                span.setStatus(StatusCode.OK);
                return;
            }

            final var result = resultOpt.get();

            final var responseBuilder = VerifyAddressFormatResponse.newBuilder()
                    .setStatus(mapFormatStatus(result.getStatus()));

            final var corrected = PostalAddress.newBuilder()
                    .setCountryCode(countryCode);
            if (result.getAddressLines() != null) corrected.addAllAddressLines(result.getAddressLines());
            if (result.getLocality() != null) corrected.setLocality(result.getLocality());
            if (result.getAdministrativeArea() != null) corrected.setAdministrativeArea(result.getAdministrativeArea());
            if (result.getPostalCode() != null) corrected.setPostalCode(result.getPostalCode());
            if (result.getSubLocality() != null) corrected.setSubLocality(result.getSubLocality());
            responseBuilder.setCorrectedAddress(corrected.build());

            for (final var issue : result.getIssues()) {
                responseBuilder.addIssues(FormatIssue.newBuilder()
                        .setField(issue.getField())
                        .setSeverity(mapSeverity(issue.getSeverity()))
                        .setCode(FormatIssueCode.forNumber(issue.getCode()))
                        .setOriginalValue(issue.getOriginalValue())
                        .setCorrectedValue(issue.getCorrectedValue())
                        .build());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage()).asRuntimeException());
        } finally {
            span.end();
        }
    }

    @Override
    public void getProviders(final GetProvidersRequest request,
                             final StreamObserver<GetProvidersResponse> responseObserver) {
        final var span = startGrpcSpan("GetProviders");
        try (final var scope = span.makeCurrent()) {
            final var response = GetProvidersResponse.newBuilder();

            for (final var provider : providerRegistry.getAllProviders()) {
                response.addProviders(ProviderInfo.newBuilder()
                        .setProviderId(provider.getProviderId())
                        .setDisplayName(provider.getDisplayName())
                        .addAllSupportedCountries(provider.getSupportedCountries())
                        .setEnabled(provider.isEnabled())
                        .build());
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage()).asRuntimeException());
        } finally {
            span.end();
        }
    }

    private Span startGrpcSpan(final String methodName) {
        if (!grpcTracingEnabled) {
            return Span.getInvalid();
        }
        return tracer.spanBuilder("AddressService/" + methodName)
                .setAttribute(AttributeKey.stringKey("rpc.service"), "AddressService")
                .setAttribute(AttributeKey.stringKey("rpc.method"), methodName)
                .startSpan();
    }

    private VerificationStatus mapVerificationStatus(final VerificationResult.Status status) {
        return switch (status) {
            case VERIFIED -> VerificationStatus.VERIFIED;
            case VERIFIED_WITH_CORRECTIONS -> VerificationStatus.VERIFIED_WITH_CORRECTIONS;
            case INVALID -> VerificationStatus.INVALID;
            case PROVIDER_ERROR -> VerificationStatus.PROVIDER_ERROR;
        };
    }

    private FormatStatus mapFormatStatus(final FormatVerificationResult.Status status) {
        return switch (status) {
            case VALID -> FormatStatus.FORMAT_VALID;
            case CORRECTED -> FormatStatus.FORMAT_CORRECTED;
            case INVALID -> FormatStatus.FORMAT_INVALID;
        };
    }

    private FormatIssueSeverity mapSeverity(final FormatVerificationResult.Severity severity) {
        return switch (severity) {
            case ERROR -> FormatIssueSeverity.ERROR;
            case WARNING -> FormatIssueSeverity.WARNING;
            case INFO -> FormatIssueSeverity.INFO;
        };
    }
}
