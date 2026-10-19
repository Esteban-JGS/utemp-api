package org.una.programmingIII.UTEMP_Project.services;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class EmailNotificationObserverTest {

    @InjectMocks
    private EmailNotificationObserver emailNotificationObserver;

    @Mock
    private JavaMailSender mailSender;

    // Real message (no session, never transported) so the content written by MimeMessageHelper can be inspected
    private MimeMessage mimeMessage;

    @Spy
    private Logger logger = org.slf4j.LoggerFactory.getLogger(EmailNotificationObserver.class);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mimeMessage = new MimeMessage((Session) null);
    }

    @Test
    void testUpdate_WhenValidEmail_ShouldSendEmail() throws MessagingException, IOException {
        // Arrange
        String eventType = "USER_ENROLLED_QUE_TAL_BRO";
        String message = "Your enrollment is successful!";
        String mail = "estebangranados147@gmail.com";

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailNotificationObserver.update(eventType, message, mail);

        verify(mailSender, times(1)).send(mimeMessage);
        assertEquals("Notification", mimeMessage.getSubject());
        assertArrayEquals(new Address[]{new InternetAddress(mail)}, mimeMessage.getRecipients(Message.RecipientType.TO));
        String body = findTextBody(mimeMessage.getContent());
        assertNotNull(body);
        assertTrue(body.contains("<p>" + message + "</p>"));
    }

    @Test
    void testSendEmail_WhenInvalidEmail_ShouldNotSendEmail() {
        // Arrange
        String invalidEmail = "invalid-email";
        String subject = "Test Subject";
        String body = "Test Body";

        // Act
        emailNotificationObserver.sendEmail(invalidEmail, subject, body);

        // Assert
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testSendEmail_WhenMailServerFails_ShouldOnlyLogAWarning(CapturedOutput output) {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Mail server connection failed")).when(mailSender).send(mimeMessage);

        // Runs on the async executor: an exception here would only end up as a stack trace in the logs
        assertDoesNotThrow(() -> emailNotificationObserver.sendEmail("student@utemp.example", "Subject", "Body"));

        assertTrue(output.getOut().contains("WARN"));
        assertTrue(output.getOut().contains("Failed to send email to student@utemp.example: Mail server connection failed"));
        assertFalse(output.getOut().contains("	at "), "no stack trace");
    }

    @Test
    void testCreateHtmlMessage() {
        // Arrange
        String eventType = "USER_ENROLLED";
        String message = "Your enrollment is successful!";

        // Act
        String htmlMessage = emailNotificationObserver.createHtmlMessage(eventType, message);

        // Assert
        assertTrue(htmlMessage.startsWith("<html>"));
        assertTrue(htmlMessage.endsWith("</html>"));
        assertTrue(htmlMessage.contains("<p>" + message + "</p>"));
    }

    private static String findTextBody(Object content) throws MessagingException, IOException {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = findTextBody(multipart.getBodyPart(i).getContent());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }
}
