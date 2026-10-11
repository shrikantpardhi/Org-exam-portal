/**
 * Communication module boundary.
 *
 * <p>Owns Notification, EmailTemplate, and Announcement. Responsible for in-app notifications,
 * email, announcements, delivery status, and retries.
 *
 * <p>P0 establishes this boundary only; the module is implemented in P10.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §39}.
 */
package com.company.exam.communication;
