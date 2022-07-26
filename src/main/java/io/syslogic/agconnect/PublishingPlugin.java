package io.syslogic.agconnect;

import com.android.build.api.dsl.ApkSigningConfig;
import com.android.build.api.dsl.ApplicationBuildType;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.ProductFlavor;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;

import io.syslogic.agconnect.constants.ArtifactType;
import io.syslogic.agconnect.task.AppInfoTask;
import io.syslogic.agconnect.task.AppIdTask;
import io.syslogic.agconnect.task.HelpTask;
import io.syslogic.agconnect.task.PublishingTask;
import io.syslogic.agconnect.util.StringUtils;

/**
 * Huawei AppGallery Connect Publishing Plugin
 *
 * @see <a href="https://developer.huawei.com/consumer/en/service/josp/agc/index.html">Console</a>
 * @author Martin Zeitler
 */
@SuppressWarnings("unused")
class PublishingPlugin implements Plugin<Project> {

    private @Nullable String configFile = null;
    private @NotNull Boolean logHttp = false;
    private @NotNull Boolean verbose = false;

    private @Nullable PublishingExtension extension;
    private @NotNull final String taskGroup = "agconnect";

    /** It depends on :assembleRelease or :bundleRelease */
    @Override
    public void apply(@NotNull Project project) {

        /* Check Preconditions. */
        if (! preconditionsMet(project)) {return;}

        /* Create project extension `agcPublishing`. */
        this.extension = project.getExtensions().create("agcPublishing", PublishingExtensionImpl.class);

        /* Apply the default path for the API client configuration file. */
        this.configFile = project.getRootProject().getProjectDir().getAbsolutePath() +
                File.separator + "credentials" + File.separator + "agc-apiclient.json";

        /* Project before evaluate: register task `:welp` */
        registerHelpTask(project,"welp");

        /* Project after evaluate. */
        project.afterEvaluate(it -> {

            /*
             * Apply buildTypes.release.signingConfig to buildTypes.debug.signingConfig.
             * This may permit uploading debug AAB. TODO: this should be configurable.
             */
            // setDebugSigningConfig(project);

            /* Loop product flavors and build-types. */
            String[] buildTypes = getBuildTypes(project);
            String[] productFlavors = getProductFlavors(project);
            String taskName;

            /* TODO: consider absent productFlavors */
            if (productFlavors.length == 0) {

                System.out.println("\nNo product flavors were detected.");

                /* Loop build-types. */
                for (String buildType : buildTypes) {

                    /* Loop artifact-types APK & AAB. */
                    for (String artifactType : new String[] {ArtifactType.APK, ArtifactType.AAB}) {

                        /* Check if file `agconnect-services.json` is present. */
                        String appConfigFile = getAppConfigPath(project, buildType);
                        if (appConfigFile != null) {

                            /* Apply values provided by the PublishingExtension. */
                            if (! extension.getConfigFile().isEmpty()) {
                                if (! new File(extension.getConfigFile()).exists()) {
                                    System.err.println("AGConnect API config not found: " + extension.getConfigFile());
                                    System.err.println("Reverting to the default value: " + configFile);
                                } else {
                                    configFile = extension.getConfigFile();
                                }
                            }
                            if (extension.getLogHttp()) {logHttp = extension.getLogHttp();}
                            if (extension.getVerbose()) {verbose = extension.getVerbose();}

                            /* Task :publishDebugAab will fail, because the AAB is signed with the upload key. */
                            if (!artifactType.equals(ArtifactType.AAB) || !buildType.equals("debug")) {

                                /* Register Tasks: Publish. */
                                taskName = "publish" + StringUtils.capitalize(artifactType);
                                registerPublishingTask(project, taskName, appConfigFile, artifactType, buildType, null, null);
                            }

                            /* Register Tasks: AppInfo */
                            taskName = "getAppInfo" + StringUtils.capitalize(buildType);
                            registerAppInfoTask(project, taskName, appConfigFile, buildType);

                            /* Register Tasks: AppId */
                            taskName = "getAppId" + StringUtils.capitalize(buildType);
                            registerAppIdTask(project, taskName, appConfigFile, buildType);
                        }
                    }
                }

            } else {

                System.out.println("\n" + productFlavors.length + " product flavors were detected: " + stringArrayToCsv(productFlavors));

                /* Loop product flavors and build-types. */
                for (String productFlavor : productFlavors) {
                    for (String buildType : buildTypes) {

                        /* Loop the variants per each single flavor and build-type. */
                        String[] variants = getVariants(new String[] {productFlavor}, new String[] {buildType});
                        for (String buildVariant : variants) {

                            /* Loop artifact-types APK & AAB. */
                            for (String artifactType : new String[] {ArtifactType.APK, ArtifactType.AAB}) {

                                /* Check if file `agconnect-services.json` is present. */
                                String appConfigFile = getAppConfigPath(project, buildType, buildVariant);
                                if (appConfigFile != null) {

                                    /* Apply values provided by the PublishingExtension. */
                                    if (! extension.getConfigFile().isEmpty()) {
                                        if (! new File(extension.getConfigFile()).exists()) {
                                            System.err.println("AGConnect API config not found: " + extension.getConfigFile());
                                            System.err.println("Reverting to the default value: " + configFile);
                                        } else {
                                            configFile = extension.getConfigFile();
                                        }
                                    }
                                    if (extension.getLogHttp()) {logHttp = extension.getLogHttp();}
                                    if (extension.getVerbose()) {verbose = extension.getVerbose();}

                                    /* Task :publishDebugAab will fail, because the AAB is signed with the upload key. */
                                    if (! artifactType.equals(ArtifactType.AAB) || !buildType.equals("debug")) {

                                        /* Register Tasks: Publish. */
                                        taskName = "publish" + StringUtils.capitalize(buildVariant) + StringUtils.capitalize(artifactType);
                                        registerPublishingTask(project, taskName, appConfigFile, artifactType, buildType, buildVariant, productFlavor);
                                    }

                                    /* Register Tasks: AppInfo */
                                    taskName = "getAppInfo" + StringUtils.capitalize(buildType);
                                    registerAppInfoTask(project, taskName, appConfigFile, buildType);

                                    /* Register Tasks: AppId */
                                    taskName = "getAppId" + StringUtils.capitalize(buildType);
                                    registerAppIdTask(project, taskName, appConfigFile, buildType);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    void registerHelpTask(@NotNull Project project, @NotNull String taskName) {
        if (project.getTasks().findByName(taskName) == null) {
            project.getTasks().register(taskName, HelpTask.class, task -> task.setGroup(taskGroup));
        }
    }

    void registerAppIdTask(
            @NotNull Project project, @NotNull String taskName,
            @NotNull String appConfigFile, @NotNull String buildType
    ) {
        if (project.getTasks().findByName(taskName) == null) {
            String apiConfigFile = configFile;
            project.getTasks().register(taskName, AppIdTask.class, task -> {
                task.setGroup(taskGroup);
                task.getApiConfigFile().set(apiConfigFile);
                task.getAppConfigFile().set(appConfigFile);
                task.getBuildType().set(buildType);
                task.getLogHttp().set(logHttp);
                task.getVerbose().set(verbose);
            });
        }
    }

    void registerAppInfoTask(
            @NotNull Project project, @NotNull String taskName,
            @NotNull String appConfigFile, @NotNull String buildType
    ) {
        if (project.getTasks().findByName(taskName) == null) {
            String apiConfigFile = configFile;
            project.getTasks().register(taskName, AppInfoTask.class, task -> {
                task.setGroup(taskGroup);
                task.getApiConfigFile().set(apiConfigFile);
                task.getAppConfigFile().set(appConfigFile);
                task.getBuildType().set(buildType);
                task.getLogHttp().set(logHttp);
                task.getVerbose().set(verbose);
            });
        }
    }

    void registerPublishingTask(
            @NotNull Project project, @NotNull String taskName, @NotNull String appConfigFile,
            @NotNull String artifactType, @NotNull String buildType,
            @Nullable String buildVariant, @Nullable String productFlavor
    ) {
        if (project.getTasks().findByName(taskName) == null) {
            String apiConfigFile = configFile;
            project.getTasks().register(taskName, PublishingTask.class, task -> {
                task.setGroup(taskGroup);
                task.getApiConfigFile().set(apiConfigFile);
                task.getAppConfigFile().set(appConfigFile);
                task.getArtifactType().set(artifactType);
                task.getBuildType().set(buildType);
                if (buildVariant != null) {task.getBuildVariant().set(buildVariant);}
                if (productFlavor != null) {task.getProductFlavor().set(productFlavor);}
                task.getLogHttp().set(logHttp);
                task.getVerbose().set(verbose);

                /* the name of the build task may vary */
                String buildTask;
                if (buildVariant != null) {
                    buildTask = getBuildTask(project, artifactType, buildVariant);
                } else {
                    buildTask = getBuildTask(project, artifactType, buildType);
                }

                /* publish* tasks depend on assemble or bundle. */
                task.dependsOn(buildTask);
            });
        }
    }

    @NotNull
    String stringArrayToCsv(@NotNull String[] data) {
        if (data.length == 0) {return "";}
        return String.join(", ", data);
    }

    /** Obtain Android ApplicationBuildType, which have a ApkSigningConfig. */
    @NotNull
    @SuppressWarnings("UnstableApiUsage")
    String[] getBuildTypes(@NotNull Project project) {
        ArrayList<String> buildTypes = new ArrayList<>();
        ApplicationExtension android = (ApplicationExtension) project.getExtensions().getByName("android");
        for (ApplicationBuildType buildType : android.getBuildTypes()) {
            ApkSigningConfig asc = buildType.getSigningConfig();
            if (asc != null) {
                // String path = "android.buildTypes." + buildType.getName() + ".signingConfig";
                if (asc.getStoreFile()     == null || !asc.getStoreFile().exists()    ) {continue;}
                if (asc.getStorePassword() == null || asc.getStorePassword().isEmpty()) {continue;}
                if (asc.getKeyPassword()   == null || asc.getKeyPassword().isEmpty()  ) {continue;}
                if (asc.getKeyAlias()      == null || asc.getKeyAlias().isEmpty()     ) {continue;}
                buildTypes.add(buildType.getName());
            }
        }
        return buildTypes.toArray(new String[0]);
    }

    @SuppressWarnings("UnstableApiUsage")
    private void setDebugSigningConfig(@NotNull Project project) {
        ApplicationExtension android = (ApplicationExtension) project.getExtensions().getByName("android");
        NamedDomainObjectContainer<? extends ApplicationBuildType> buildTypes = android.getBuildTypes();
        ApplicationBuildType btRelease = buildTypes.getByName("release");
        ApplicationBuildType btDebug = buildTypes.getByName("debug");
        ApkSigningConfig apkSigningConfig = btRelease.getSigningConfig();
        btDebug.setSigningConfig(apkSigningConfig);
    }

    String[] getProductFlavors(@NotNull Project project) {
        ArrayList<String> items = new ArrayList<>();
        ApplicationExtension android = (ApplicationExtension) project.getExtensions().getByName("android");
        for (ProductFlavor productFlavor : android.getProductFlavors()) {items.add(productFlavor.getName());}
        return items.toArray(new String[0]);
    }

    String[] getVariants(@NotNull String[] productFlavors, @NotNull String[] buildTypes) {
        if (productFlavors.length == 0) {return buildTypes;}
        ArrayList<String> items = new ArrayList<>();
        for (String buildType : buildTypes) {
            for (String productFlavor : productFlavors) {
                items.add(productFlavor + StringUtils.capitalize(buildType));
            }
        }
        return items.toArray(new String[0]);
    }
    
    /** Check if Android and AGConnect Gradle plugins were loaded. */
    public boolean preconditionsMet(@NotNull Project project) {
        if (! project.getPluginManager().hasPlugin("com.android.application") || ! project.getPluginManager().hasPlugin("com.huawei.agconnect")) {
            if (! project.getPluginManager().hasPlugin("com.android.application")) {
                System.err.println("Plugin 'agconnect-publishing' depends on 'com.android.application'.");
            }
            if (! project.getPluginManager().hasPlugin("com.huawei.agconnect")) {
                System.err.println("Plugin 'agconnect-publishing' depends on 'com.huawei.agconnect'.");
            }
            return false;
        }
        return true;
    }

    @Nullable
    private String getAppConfigPath(@NotNull Project project, @NotNull String buildType) {
        String basePath = project.getProjectDir().getAbsolutePath() + File.separator;
        String path = basePath + "src" + File.separator + buildType + File.separator + "agconnect-services.json";
        if (new File(path).exists()) {return path;}
        path = basePath + "src" + File.separator + "agconnect-services.json";
        if (new File(path).exists()) {return path;}
        return null;
    }

    @Nullable
    private String getAppConfigPath(@NotNull Project project, @NotNull String buildType, @NotNull String variant) {
        String basePath = project.getProjectDir().getAbsolutePath() + File.separator;
        String path = basePath + "src" + File.separator + buildType + File.separator + "agconnect-services.json";
        if (new File(path).exists()) {return path;}
        path = basePath + "src" + File.separator + variant + File.separator + "agconnect-services.json";
        if (new File(path).exists()) {return path;}
        return null;
    }

    @NotNull
    private String getBuildTask(@NotNull Project project, @NotNull String artifactType, @NotNull String variant) {
        String task = "assembleRelease";
        if (artifactType.equals(ArtifactType.AAB)) {task = "bundle" + StringUtils.capitalize(variant);}
        else if (artifactType.equals(ArtifactType.APK)) {task = "assemble" + StringUtils.capitalize(variant);}
        return ":" + project.getName() + ":" + task;
    }
}
