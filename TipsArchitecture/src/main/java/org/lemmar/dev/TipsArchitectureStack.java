package org.lemmar.dev;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;

public class TipsArchitectureStack extends Stack {
    public TipsArchitectureStack(final App scope, final String id) {
        this(scope, id, null);
    }

    public TipsArchitectureStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Create a Lambda function using the code in Tips
        Function tipFunction = Function.Builder.create(this, "TipsLambdaHandler")
                .runtime(Runtime.JAVA_17)  // Execution environment
                .code(Code.fromAsset("../Tips/build/libs/Tips-1.0-SNAPSHOT-all.jar"))  // Code from Tips
                .handler("org.lemmar.dev.TipOfTheDay::handleRequest")
                .build();

        // Create an EventBridge rule to trigger the Lambda at 9am Pacific time daily
        Rule dailyTipRule = Rule.Builder.create(this, "DailyTipRule")
                .schedule(Schedule.expression("cron(0 9 * * ? *)"))  // Run every day at 9am
                .build();

        // Add the Lambda as a target for the EventBridge rule
        dailyTipRule.addTarget(new LambdaFunction(tipFunction));
    }
}
