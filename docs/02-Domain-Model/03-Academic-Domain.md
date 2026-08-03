# Domain Model & ERD v1.0

## 03. Academic Domain

## Purpose

The Academic Domain manages the exam taxonomy used to classify questions and assessments.

## Core Entities

- Exam
- Subject
- Chapter
- Topic
- SubTopic
- Language
- Tag

## Entity Relationships

- One Exam has many Subjects
- One Subject has many Chapters
- One Chapter has many Topics
- One Topic has many SubTopics
- Languages and Tags are reusable masters

## Responsibilities

- Maintain a standardized academic hierarchy
- Support multi-language assessments
- Support tagging and classification for search and analytics

## Business Rules

- The hierarchy follows Exam → Subject → Chapter → Topic → Sub Topic
- Child records cannot exist without their parent
- Languages and Tags are shared reusable records

## Status

Part of the frozen Version 1.0 architecture baseline.
