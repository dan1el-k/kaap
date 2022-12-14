package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSpec extends ValidableSpec<GlobalSpec> implements WithDefaults {


    private static final Supplier<TlsConfig> DEFAULT_TLS_CONFIG = () -> TlsConfig.builder()
            .enabled(false)
            .defaultSecretName("pulsar-tls")
            .build();

    private static final Supplier<AuthConfig> DEFAULT_AUTH_CONFIG = () -> AuthConfig.builder()
            .enabled(false)
            .token(AuthConfig.TokenConfig.builder()
                    .publicKeyFile("my-public.key")
                    .privateKeyFile("my-private.key")
                    .superUserRoles(List.of("superuser", "admin", "websocket", "proxy"))
                    .proxyRoles(List.of("proxy"))
                    .provisioner(AuthConfig.TokenAuthProvisionerConfig.builder()
                            .initialize(true)
                            .image("datastax/burnell:latest")
                            .imagePullPolicy("IfNotPresent")
                            .rbac(AuthConfig.TokenAuthProvisionerConfig.RbacConfig.builder()
                                    .create(true)
                                    .namespaced(true)
                                    .build())
                            .build())
                    .build())
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Components {
        @JsonPropertyDescription("Zookeeper base name. Default value is 'zookeeper'.")
        private String zookeeperBaseName;
        @JsonPropertyDescription("BookKeeper base name. Default value is 'bookkeeper'.")
        private String bookkeeperBaseName;
        @JsonPropertyDescription("Broker base name. Default value is 'broker'.")
        private String brokerBaseName;
        @JsonPropertyDescription("Proxy base name. Default value is 'proxy'.")
        private String proxyBaseName;
        @JsonPropertyDescription("Autorecovery base name. Default value is 'autorecovery'.")
        private String autorecoveryBaseName;
        @JsonPropertyDescription("Bastion base name. Default value is 'bastion'.")
        private String bastionBaseName;
        @JsonPropertyDescription("Functions Worker base name. Default value is 'function'.")
        private String functionsWorkerBaseName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsConfig {
        @JsonPropertyDescription("Global switch to turn on or off the TLS configurations.")
        private Boolean enabled;
        @JsonPropertyDescription("Default secret name.")
        private String defaultSecretName;
        @JsonPropertyDescription("TLS configurations related to the ZooKeeper component.")
        TlsEntryConfig zookeeper;
        @JsonPropertyDescription("TLS configurations related to the BookKeeper component.")
        TlsEntryConfig bookkeeper;
        @JsonPropertyDescription("TLS configurations related to the broker component.")
        TlsEntryConfig broker;
        @JsonPropertyDescription("TLS configurations related to the proxy component.")
        TlsEntryConfig proxy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        boolean enabled;
        @JsonPropertyDescription("Enable certificates for this component.")
        String tlsSecretName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GlobalStorageConfig {
        @JsonPropertyDescription("Indicates if a StorageClass is used. The operator will create the StorageClass if needed.")
        private StorageClassConfig storageClass;
        @JsonPropertyDescription("Indicates if an already existing storage class should be used.")
        private String existingStorageClassName;
    }


    @NotNull
    @Required
    @JsonPropertyDescription("Pulsar cluster base name.")
    private String name;
    @JsonPropertyDescription("Pulsar cluster components names.")
    private Components components;
    @JsonPropertyDescription("Additional DNS config for each pod created by the operator.")
    private PodDNSConfig dnsConfig;
    @JsonPropertyDescription("""
            The domain name for your kubernetes cluster.
            This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
            It's used to fully qualify service names when configuring Pulsar.
            The default value is 'cluster.local'.
            """)
    private String kubernetesClusterDomain;
    @JsonPropertyDescription("Global node selector. If set, this will apply to all components.")
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription("TLS configuration for the cluster.")
    private TlsConfig tls;
    @JsonPropertyDescription("""
            If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptDir
            """)
    private Boolean persistence;
    @JsonPropertyDescription("""
            By default, Kubernetes will not restart pods when only their configmap is changed. This setting will restart pods when their configmap is changed using an annotation that calculates the checksum of the configmap.
            """)
    private Boolean restartOnConfigMapChange;

    @JsonPropertyDescription("""
            Auth stuff.
            """)
    private AuthConfig auth;

    // overridable parameters
    @JsonPropertyDescription("Default Pulsar image to use. Any components can be configured to use a different image.")
    private String image;
    @JsonPropertyDescription("Default Pulsar image pull policy to use. Any components can be configured to use a different image pull policy. Default value is 'IfNotPresent'.")
    private String imagePullPolicy;
    @JsonPropertyDescription("Storage configuration.")
    private GlobalStorageConfig storage;


    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        applyComponentsDefaults();

        if (kubernetesClusterDomain == null) {
            kubernetesClusterDomain = "cluster.local";
        }
        if (imagePullPolicy == null) {
            imagePullPolicy = "IfNotPresent";
        }
        if (persistence == null) {
            persistence = true;
        }
        if (restartOnConfigMapChange == null) {
            restartOnConfigMapChange = false;
        }
        if (storage == null) {
            storage = new GlobalStorageConfig();
        }
        if (storage.getStorageClass() == null && storage.getExistingStorageClassName() == null) {
            storage.setExistingStorageClassName("default");
        }
        if (storage.getStorageClass() != null && storage.getStorageClass().getReclaimPolicy() == null) {
            storage.getStorageClass().setReclaimPolicy("Retain");
        }
        applyTlsDefaults();
        applyAuthDefaults();
    }

    private void applyTlsDefaults() {
        if (tls == null) {
            tls = DEFAULT_TLS_CONFIG.get();
        } else {
            tls.setEnabled(ObjectUtils.getFirstNonNull(
                    () -> tls.getEnabled(),
                    () -> DEFAULT_TLS_CONFIG.get().getEnabled())
            );
            tls.setDefaultSecretName(ObjectUtils.getFirstNonNull(
                    () -> tls.getDefaultSecretName(),
                    () -> DEFAULT_TLS_CONFIG.get().getDefaultSecretName())
            );
        }
    }

    private void applyComponentsDefaults() {
        if (components == null) {
            components = new Components();
        }
        components.setZookeeperBaseName(ObjectUtils.firstNonNull(components.getZookeeperBaseName(), "zookeeper"));
        components.setBookkeeperBaseName(ObjectUtils.firstNonNull(components.getBookkeeperBaseName(), "bookkeeper"));
        components.setBrokerBaseName(ObjectUtils.firstNonNull(components.getBrokerBaseName(), "broker"));
        components.setProxyBaseName(ObjectUtils.firstNonNull(components.getProxyBaseName(), "proxy"));
        components.setAutorecoveryBaseName(ObjectUtils.firstNonNull(components.getAutorecoveryBaseName(), "autorecovery"));
        components.setBastionBaseName(ObjectUtils.firstNonNull(components.getBastionBaseName(), "bastion"));
        components.setFunctionsWorkerBaseName(ObjectUtils.firstNonNull(components.getFunctionsWorkerBaseName(), "function"));
    }

    private void applyAuthDefaults() {
        if (auth == null) {
            auth = DEFAULT_AUTH_CONFIG.get();
        } else {
            ConfigUtil.applyDefaultsWithReflection(auth, DEFAULT_AUTH_CONFIG);
            if (auth.getToken() == null) {
                auth.setToken(DEFAULT_AUTH_CONFIG.get().getToken());
            }
            final AuthConfig.TokenConfig tokenConfig = auth.getToken();
            if (tokenConfig != null) {
                ConfigUtil.applyDefaultsWithReflection(auth.getToken(), () -> DEFAULT_AUTH_CONFIG.get().getToken());
                if (tokenConfig.getProvisioner() == null) {
                    tokenConfig.setProvisioner(DEFAULT_AUTH_CONFIG.get().getToken().getProvisioner());
                } else {
                    ConfigUtil.applyDefaultsWithReflection(tokenConfig.getProvisioner(),
                            () -> DEFAULT_AUTH_CONFIG.get().getToken().getProvisioner());
                }
                if (tokenConfig.getProvisioner() != null) {
                    final AuthConfig.TokenAuthProvisionerConfig prov = tokenConfig.getProvisioner();
                    if (prov.getRbac() == null) {
                        prov.setRbac(DEFAULT_AUTH_CONFIG.get().getToken().getProvisioner().getRbac());
                    } else {
                        ConfigUtil.applyDefaultsWithReflection(prov.getRbac(),
                                () -> DEFAULT_AUTH_CONFIG.get().getToken().getProvisioner().getRbac());
                    }
                }
            }
        }
    }

    @Override
    public boolean isValid(GlobalSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
