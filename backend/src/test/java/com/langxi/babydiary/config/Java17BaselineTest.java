package com.langxi.babydiary.config;

import com.langxi.babydiary.service.AnniversaryService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class Java17BaselineTest {

    @Test
    void backendBuildTargetsJava17Release() throws Exception {
        String pom = read("pom.xml");

        assertThat(pom).contains("<java.version>17</java.version>");
        assertThat(pom).contains("<release>${java.version}</release>");
        assertThat(pom).contains("<parameters>true</parameters>");
        assertThat(pom).doesNotContain("<source>1.8</source>");
        assertThat(pom).doesNotContain("<target>1.8</target>");
    }

    @Test
    void verificationAndDeploymentScriptsUseJava17HomeByDefault() throws Exception {
        String verifyScript = read("../scripts/verify.sh");
        String deployScript = read("../scripts/deploy.sh");

        assertThat(verifyScript).contains("scripts/java-env.sh");
        assertThat(verifyScript).contains("mvn -q clean test");
        assertThat(deployScript).contains("scripts/java-env.sh");
        assertThat(deployScript).contains("mvn -q -DskipTests clean package -f backend/pom.xml");
        assertThat(read("../scripts/java-env.sh")).contains("JAVA_HOME_DEFAULT=\"/usr/lib/jvm/java-17-openjdk-amd64\"");
    }

    @Test
    void compiledClassesExposeRuntimeParameterNames() throws Exception {
        Method method = AnniversaryService.class.getDeclaredMethod("findByUserId", Integer.class);

        assertThat(method.getParameters()[0].isNamePresent()).isTrue();
        assertThat(method.getParameters()[0].getName()).isEqualTo("userId");
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
