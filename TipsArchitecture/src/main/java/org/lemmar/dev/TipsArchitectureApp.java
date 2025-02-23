package org.lemmar.dev;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class TipsArchitectureApp {
    public static void main(final String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .region(System.getenv("us-west-1"))
                .build();

        new TipsArchitectureStack(app, "TipsArchitectureStack", StackProps.builder()
                .env(env)
                .build());

        app.synth();
    }
}

