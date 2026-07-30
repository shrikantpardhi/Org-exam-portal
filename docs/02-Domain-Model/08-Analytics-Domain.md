# Domain Model & ERD v1.0

## 08. Analytics Domain

## Purpose

The Analytics Domain stores aggregated performance and operational metrics derived from assessments, attempts, questions, topics, and organization activity.

## Core Entities

- StudentAnalytics
- AssessmentAnalytics
- QuestionAnalytics
- TopicAnalytics
- OrganizationAnalytics

## Entity Relationships

- One User has one StudentAnalytics record
- One Assessment has one AssessmentAnalytics record
- One Question has one QuestionAnalytics record
- One Topic has one TopicAnalytics record
- One Organization has one OrganizationAnalytics record

## Responsibilities

- Support student performance dashboards
- Support assessment quality dashboards
- Support question and topic quality analysis
- Support organization-wide summary reporting

## Business Rules

- Analytics are derived from transactional records
- Analytics may be recalculated by background jobs
- Analytics must not modify source transactional data

## Status

Part of the frozen Version 1.0 architecture baseline.
