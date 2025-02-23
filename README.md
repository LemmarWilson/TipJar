# TipJar

**TipJar** is a simple, serverless application that delivers daily tips via email and text message. It uses AWS Lambda for the core logic, AWS EventBridge Scheduler to trigger Lambda at a specific time (9 AM PT), and Twilio + SMTP for messaging. It also leverages an Excel file to store topics and prompts, which are read by the Lambda, and optionally calls OpenAI to generate or enhance content.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup & Deployment](#setup--deployment)
- [Environment Variables](#environment-variables)
- [Usage](#usage)
- [How It Works](#how-it-works)
- [Future Enhancements](#future-enhancements)

---

## Overview

**TipJar** is designed for simplicity. Each day, a Lambda function runs at 9 AM Pacific Time, generating a short tip (using data from an Excel file and optionally enhanced by OpenAI) and dispatching it via:
- **Email** (through SMTP).
- **SMS** (via Twilio).

Everything is orchestrated and deployed using the [AWS CDK](https://aws.amazon.com/cdk/) in Java, ensuring infrastructure-as-code best practices.

---

## Architecture

Below is a high-level view of the **TipJar** architecture:

1. **AWS EventBridge Scheduler**  
   - **Trigger**: Runs the Lambda function daily at 9 AM PT (cron job).
2. **AWS Lambda**  
   - **Handler**: Loads an Excel file (bundled in the Lambda package), picks a random topic & prompt, optionally calls OpenAI to generate a content snippet, and then sends it out via SMTP email and Twilio SMS.
3. **SMTP & Twilio**  
   - **Email**: JavaMail/SMTP sends the tip to a configured email address.
   - **SMS**: Twilio’s Java SDK sends the tip to one or more phone numbers.


---

## Features

- **Scheduled Tips**: Automatically sends a tip every day at 9 AM PT.
- **Excel-based Prompts**: An Excel file (`prompts.xlsx`) stores topics and prompts, making it easy to add or modify content.
- **OpenAI Integration** (Optional): You can query OpenAI for dynamic text generation.
- **Multi-Channel Delivery**: Tips can be sent via email and text message.
- **Serverless Deployment**: Managed with AWS Lambda and EventBridge Scheduler, so there’s no need to manage servers.
- **Infrastructure as Code**: Built and deployed using AWS CDK in Java.

---

## Project Structure

```plaintext
TipJar/
├── Tips/                   <-- Lambda codebase (Java with Gradle)
│   ├── src/main/java/org/lemmar/dev/
│   │   └── TipOfTheDay.java
│   ├── src/main/resources/
│   │   └── prompts.xlsx   <-- Excel file with topics & prompts
│   └── build.gradle       <-- Gradle build file (uses Shadow plugin to build fat JAR)
└── TipsArchitecture/       <-- AWS CDK project (Java with Maven)
    ├── src/main/java/org/lemmar/dev/
    │   ├── TipsArchitectureStack.java
    │   └── TipsArchitectureApp.java
    ├── pom.xml            <-- Maven build file
    └── cdk.json           <-- CDK configuration
```

1. **Tips**:
   - Contains the actual Lambda code that:
      - Reads the Excel file.
      - Optionally queries OpenAI.
      - Sends messages via SMTP/Twilio.
   - Built with Gradle to create a fat JAR including all dependencies (Apache POI, Twilio, JavaMail, etc.).

2. **TipsArchitecture**:
   - AWS CDK (Java, Maven) project.
   - Defines the `TipsArchitectureStack`, which:
      - Creates the Lambda function from the fat JAR in `../Tips/build/libs/`.
      - Sets up an EventBridge rule to trigger it daily at 9 AM PT.
   - Deployment via `cdk synth` and `cdk deploy`.

---

## Prerequisites

1. **AWS CLI** configured for your account.
2. **AWS CDK** installed globally (`npm install -g aws-cdk`).
3. **Java 11 or higher** installed.
4. **Gradle** for building the Lambda code (in `Tips`).
5. **Maven** for the CDK project (in `TipsArchitecture`).
6. **An AWS account** with permissions to deploy Lambda, EventBridge, IAM, etc.

---

## Setup & Deployment

Follow these steps to deploy **TipJar**:

1. **Build the Lambda Fat JAR**  
   Go to the `Tips` directory:
   ```bash
   cd Tips
   ./gradlew clean shadowJar
   ```
   This produces a self-contained JAR in `build/libs/` (e.g., `tips-all.jar`).

2. **Deploy the CDK Stack**  
   Switch to the `TipsArchitecture` directory:
   ```bash
   cd ../TipsArchitecture
   mvn package
   cdk synth
   cdk deploy
   ```
   This will:
   - Create or update the `TipsStack`.
   - Upload your Lambda code (the fat JAR) and configure the EventBridge schedule.

3. **Verify the Deployment**
   - Check AWS CloudFormation for the created/updated stack.
   - Check the AWS Lambda console to confirm the function is present.
   - Check EventBridge rules for the daily schedule.

---

## Environment Variables

**TipJar** uses multiple environment variables to configure external services:

| Variable              | Description                                      |
|-----------------------|--------------------------------------------------|
| `OPENAI_API_KEY`      | API key for OpenAI (optional if not used).       |
| `EMAIL_USER`          | Sender email username (for SMTP).               |
| `EMAIL_PASSWORD`      | Sender email password (for SMTP).               |
| `EMAIL_ADDRESS`       | Recipient email address.                         |
| `TWILIO_ACCOUNT_SID`  | Twilio Account SID.                              |
| `TWILIO_AUTH_TOKEN`   | Twilio Auth Token.                               |
| `TWILIO_PHONE_NUMBER` | Twilio phone number for sending SMS.            |
| `PHONE_NUMBER`        | Recipient phone number for receiving SMS.       |

Set these either in your Lambda console’s environment variables or supply them at deployment (e.g., using AWS Secrets Manager).

---

## Usage

1. **Automatic Scheduling**
   - Every day at 9 AM PT, EventBridge calls your Lambda function.
   - The function randomly selects a prompt from the Excel file.
   - If OpenAI is enabled, it makes a request to generate an expanded tip.
   - The tip is sent via email and/or SMS.

2. **On-Demand Invocation**
   - You can also test or manually invoke the Lambda from the AWS console or CLI.

3. **Modifying Prompts**
   - Update the `prompts.xlsx` in your `src/main/resources/` directory.
   - Rebuild and redeploy your function so the new file is included.

---

## How It Works

1. **Lambda Initialization**
   - Loads the `prompts.xlsx` file from the JAR’s resources.
2. **Tip Generation**
   - Chooses a random row from the sheet (e.g., `prompts` or `htmlcss`).
   - Sends the content (plus instructions) to OpenAI (if enabled).
   - Receives a generated tip from OpenAI, or just uses the raw prompt.
3. **Notification Dispatch**
   - Sends the tip via Twilio SMS.
   - Sends the tip via SMTP email.

With **TipJar**, you can easily manage daily tips without maintaining any servers.

---

## Future Enhancements

- **Database Integration**: Instead of storing prompts in an Excel file, consider using a relational or NoSQL database to easily add, modify, and query prompts. This would also open the door for user-specific customization or tracking which prompts have been sent.
- **Dynamic Scheduling**: Allow users to specify their preferred time (or frequency) for receiving tips. You could store schedules in a database and programmatically create EventBridge rules or an alternative scheduling approach.
- **Two-Way Interaction**: Enable users to text back with specific topic requests. A Twilio webhook could receive incoming texts and dynamically generate or send tips on-demand.
- **Multi-User Support**: Provide a registration flow for multiple users. Each user could specify their phone number, email, and topic preferences, making TipJar more personalized.
- **Analytics & Reporting**: Track open rates (for emails) or delivery metrics (for SMS) and store them in a data warehouse for insights into user engagement.
