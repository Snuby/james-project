/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.jmap.model.MessageFactory.MetaDataWithContent;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.jmap.utils.MailboxBasedHtmlTextExtractor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.tika.extractor.TikaTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MessageFactoryTest {
    private static final InMemoryId MAILBOX_ID = InMemoryId.of(18L);
    private static final ZoneId UTC_ZONE_ID = ZoneId.of("Z");
    private static final ZonedDateTime ZONED_DATE = ZonedDateTime.of(2015, 07, 14, 12, 30, 42, 0, UTC_ZONE_ID);
    private static final Date INTERNAL_DATE = Date.from(ZONED_DATE.toInstant());

    private MessageFactory messageFactory;
    private MessagePreviewGenerator messagePreview ;
    private HtmlTextExtractor htmlTextExtractor;
    
    @Before
    public void setUp() {
        htmlTextExtractor = new MailboxBasedHtmlTextExtractor(new TikaTextExtractor());

        messagePreview = new MessagePreviewGenerator();
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();

        messageFactory = new MessageFactory(messagePreview, messageContentExtractor, htmlTextExtractor);
    }
    @Test
    public void emptyMailShouldBeLoadedIntoMessage() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream("".getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(Message::getPreview, Message::getSize, Message::getSubject, Message::getHeaders, Message::getDate)
            .containsExactly("(Empty)", 0L, "", ImmutableMap.of("Date", "Tue, 14 Jul 2015 12:30:42 +0000", "MIME-Version", "1.0"), ZONED_DATE);
    }

    @Test
    public void flagsShouldBeSetIntoMessage() throws Exception {
        Flags flags = new Flags();
        flags.add(Flag.ANSWERED);
        flags.add(Flag.FLAGGED);
        flags.add(Flag.DRAFT);
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(flags)
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream("".getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(Message::isIsUnread, Message::isIsFlagged, Message::isIsAnswered, Message::isIsDraft)
            .containsExactly(true, true, true, true);
    }

    @Test
    public void headersShouldBeSetIntoMessage() throws Exception {
        String headers = "From: user <user@domain>\n"
                + "Subject: test subject\n"
                + "To: user1 <user1@domain>, user2 <user2@domain>\n"
                + "Cc: usercc <usercc@domain>\n"
                + "Bcc: userbcc <userbcc@domain>\n"
                + "Reply-To: \"user to reply to\" <user.reply.to@domain>\n"
                + "In-Reply-To: <SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>\n"
                + "Other-header: other header value";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(headers.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(headers.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        Emailer user = Emailer.builder().name("user").email("user@domain").build();
        Emailer user1 = Emailer.builder().name("user1").email("user1@domain").build();
        Emailer user2 = Emailer.builder().name("user2").email("user2@domain").build();
        Emailer usercc = Emailer.builder().name("usercc").email("usercc@domain").build();
        Emailer userbcc = Emailer.builder().name("userbcc").email("userbcc@domain").build();
        Emailer userRT = Emailer.builder().name("user to reply to").email("user.reply.to@domain").build();
        ImmutableMap<String, String> headersMap = ImmutableMap.<String, String>builder()
                .put("Cc", "usercc <usercc@domain>")
                .put("Bcc", "userbcc <userbcc@domain>")
                .put("Subject", "test subject")
                .put("From", "user <user@domain>")
                .put("To", "user1 <user1@domain>, user2 <user2@domain>")
                .put("Reply-To", "\"user to reply to\" <user.reply.to@domain>")
                .put("In-Reply-To", "<SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>")
                .put("Other-header", "other header value")
                .put("Date", "Tue, 14 Jul 2015 12:30:42 +0000")
                .put("MIME-Version", "1.0")
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        Message expected = Message.builder()
                .id(TestMessageId.of(2))
                .blobId(BlobId.of("2"))
                .threadId("2")
                .mailboxId(MAILBOX_ID)
                .inReplyToMessageId("<SNT124-W2664003139C1E520CF4F6787D30@phx.gbl>")
                .headers(headersMap)
                .from(user)
                .to(ImmutableList.of(user1, user2))
                .cc(ImmutableList.of(usercc))
                .bcc(ImmutableList.of(userbcc))
                .replyTo(ImmutableList.of(userRT))
                .subject("test subject")
                .date(ZONED_DATE)
                .size(headers.length())
                .preview("(Empty)")
                .textBody(Optional.of(""))
                .htmlBody(Optional.empty())
                .build();
        assertThat(testee).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void textBodyShouldBeSetIntoMessage() throws Exception {
        String headers = "Subject: test subject\n";
        String body = "Mail body";
        String mail = headers + "\n" + body;
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(mail.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(mail.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getTextBody()).hasValue("Mail body");
    }

    @Test
    public void textBodyShouldNotOverrideWhenItIsThere() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Subject\n"
            + "MIME-Version: 1.0\n"
            + "Content-Type: multipart/alternative;\n"
            + "\tboundary=\"----=_Part_370449_1340169331.1489506420401\"\n"
            + "\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/plain; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "My plain message\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/html; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "<a>The </a> <strong>HTML</strong> message"
        ).getBytes(Charsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .internalDate(INTERNAL_DATE)
            .size(1000)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getTextBody())
            .isPresent()
            .isEqualTo(Optional.of("My plain message"));
    }

    @Test
    public void previewShouldBeLimitedTo256Length() throws Exception {
        String headers = "Subject: test subject\n";
        String body300 = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999";
        String expectedPreview = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999" 
                + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999" 
                + "00000000001111111111222222222233333333334444444444555555";
        assertThat(body300.length()).isEqualTo(300);
        assertThat(expectedPreview.length()).isEqualTo(256);
        String mail = headers + "\n" + body300;
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(mail.length())
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(mail.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getPreview()).isEqualTo(expectedPreview);
    }
    
    @Test
    public void attachmentsShouldBeEmptyWhenNone() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(IOUtils.toByteArray(ClassLoader.getSystemResourceAsStream("spamMail.eml"))))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee.getAttachments()).isEmpty();
    }
    
    @Test
    public void attachmentsShouldBeRetrievedWhenSome() throws Exception {
        String payload = "payload";
        BlobId blodId = BlobId.of("id1");
        String type = "content";
        Attachment expectedAttachment = Attachment.builder()
                .blobId(blodId)
                .size(payload.length())
                .type(type)
                .cid("cid")
                .isInline(true)
                .build();
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(0)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream(IOUtils.toByteArray(ClassLoader.getSystemResourceAsStream("spamMail.eml"))))
                .attachments(ImmutableList.of(MessageAttachment.builder()
                        .attachment(org.apache.james.mailbox.model.Attachment.builder()
                                .attachmentId(AttachmentId.from(blodId.getRawValue()))
                                .bytes(payload.getBytes())
                                .type(type)
                                .build())
                        .cid(Cid.from("cid"))
                        .isInline(true)
                        .build()))
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getAttachments()).hasSize(1);
        assertThat(testee.getAttachments().get(0)).isEqualToComparingFieldByField(expectedAttachment);
    }

    @Test
    public void invalidAddressesShouldBeAllowed() throws Exception {
        String headers = "From: user <userdomain>\n"
            + "To: user1 <user1domain>, user2 <user2domain>\n"
            + "Cc: usercc <userccdomain>\n"
            + "Bcc: userbcc <userbccdomain>\n"
            + "Subject: test subject\n";
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(headers.length())
            .internalDate(INTERNAL_DATE)
            .content(new ByteArrayInputStream(headers.getBytes(Charsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(new TestMessageId.Factory().generate())
            .build();

        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        Emailer user = Emailer.builder().name("user").email("userdomain").allowInvalid().build();
        Emailer user1 = Emailer.builder().name("user1").email("user1domain").allowInvalid().build();
        Emailer user2 = Emailer.builder().name("user2").email("user2domain").allowInvalid().build();
        Emailer usercc = Emailer.builder().name("usercc").email("userccdomain").allowInvalid().build();
        Emailer userbcc = Emailer.builder().name("userbcc").email("userbccdomain").allowInvalid().build();

        assertThat(testee.getFrom()).contains(user);
        assertThat(testee.getTo()).contains(user1, user2);
        assertThat(testee.getCc()).contains(usercc);
        assertThat(testee.getBcc()).contains(userbcc);
    }

    @Test
    public void mailWithBigLinesShouldBeLoadedIntoMessage() throws Exception {
        MetaDataWithContent testMail = MetaDataWithContent.builder()
                .uid(MessageUid.of(2))
                .flags(new Flags(Flag.SEEN))
                .size(1010)
                .internalDate(INTERNAL_DATE)
                .content(new ByteArrayInputStream((StringUtils.repeat("0123456789", 101).getBytes(Charsets.UTF_8))))
                .attachments(ImmutableList.of())
                .mailboxId(MAILBOX_ID)
                .messageId(TestMessageId.of(2))
                .build();

        Message testee = messageFactory.fromMetaDataWithContent(testMail);
        assertThat(testee)
            .extracting(Message::getPreview, Message::getSize, Message::getSubject, Message::getHeaders, Message::getDate)
            .containsExactly("(Empty)", 1010L, "", ImmutableMap.of("Date", "Tue, 14 Jul 2015 12:30:42 +0000", "MIME-Version", "1.0"), ZONED_DATE);
    }

    @Test
    public void textBodyShouldBeSetIntoMessageInCaseOfHtmlBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "my <b>HTML</b> message").getBytes(Charsets.UTF_8));
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo("my HTML message");
        assertThat(testee.getTextBody()).hasValue("my HTML message");
        assertThat(testee.getHtmlBody()).hasValue("my <b>HTML</b> message");
    }

    @Test
    public void textBodyShouldBeEmptyInCaseOfEmptyHtmlBodyAndEmptyTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n").getBytes(Charsets.UTF_8));
        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo(MessagePreviewGenerator.NO_BODY);
        assertThat(testee.getHtmlBody()).contains("");
        assertThat(testee.getTextBody()).isEmpty();
    }

    @Test
    public void previewBodyShouldReturnTruncatedStringWithoutHtmlTagWhenHtmlBodyContainTags() throws Exception {
        String body = "This is a <b>HTML</b> mail containing <u>underlined part</u>, <i>italic part</i> and <u><i>underlined AND italic part</i></u>9999999999"
            + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
            + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
            + "000000000011111111112222222222333333333344444444445555555";
        String expected = "This is a HTML mail containing underlined part, italic part and underlined AND italic part9999999999"
            + "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999"
            + "00000000001111111111222222222233333333334444444444555555";

        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + body).getBytes(Charsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo(expected);
    }

    @Test
    public void previewBodyShouldReturnTextBodyWhenNoHtmlBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/plain\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "My plain text").getBytes(Charsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee.getPreview()).isEqualTo("My plain text");
    }

    @Test
    public void previewBodyShouldReturnStringEmptyWhenNoHtmlBodyAndNoTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Subject: message 1 subject\r\n").getBytes(Charsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee)
            .extracting(Message::getPreview, Message::getHtmlBody, Message::getTextBody)
            .containsExactly(MessagePreviewGenerator.NO_BODY, Optional.empty(), Optional.of(""));
    }

    @Test
    public void previewBodyShouldReturnStringEmptyWhenNoMeaningHtmlBodyAndNoTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("CContent-Type: text/html\r\n"
            + "Subject: message 1 subject\r\n"
            + "\r\n"
            + "<html><body></body></html>").getBytes(Charsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee)
            .extracting(Message::getPreview, Message::getHtmlBody, Message::getTextBody)
            .containsExactly(MessagePreviewGenerator.NO_BODY, Optional.of("<html><body></body></html>"), Optional.empty());
    }

    @Test
    public void previewBodyShouldReturnTextBodyWhenNoMeaningHtmlBodyAndTextBody() throws Exception {
        ByteArrayInputStream messageContent = new ByteArrayInputStream(("Subject\n"
            + "MIME-Version: 1.0\n"
            + "Content-Type: multipart/alternative;\n"
            + "\tboundary=\"----=_Part_370449_1340169331.1489506420401\"\n"
            + "\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/plain; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "My plain message\n"
            + "------=_Part_370449_1340169331.1489506420401\n"
            + "Content-Type: text/html; charset=UTF-8\n"
            + "Content-Transfer-Encoding: 7bit\n"
            + "\n"
            + "<html></html>"
        ).getBytes(Charsets.UTF_8));

        MetaDataWithContent testMail = MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .flags(new Flags(Flag.SEEN))
            .size(messageContent.read())
            .internalDate(INTERNAL_DATE)
            .content(messageContent)
            .attachments(ImmutableList.of())
            .mailboxId(MAILBOX_ID)
            .messageId(TestMessageId.of(2))
            .build();
        Message testee = messageFactory.fromMetaDataWithContent(testMail);

        assertThat(testee)
            .extracting(Message::getPreview, Message::getHtmlBody, Message::getTextBody)
            .containsExactly("My plain message", Optional.of("<html></html>"), Optional.of("My plain message"));
    }
}
