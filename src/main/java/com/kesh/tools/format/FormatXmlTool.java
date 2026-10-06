package com.kesh.tools.format;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.kesh.tools.PermissionLevel;
import com.kesh.tools.Tool;
import com.kesh.tools.ToolArguments;
import com.kesh.tools.ToolResult;
import com.kesh.tools.ToolSchema;

@Component
public class FormatXmlTool implements Tool {

    @Override
    public String name() {
        return "format_xml";
    }

    @Override
    public String description() {
        return "Pretty-print XML text locally. No network and no shell.";
    }

    @Override
    public PermissionLevel permissionLevel() {
        return PermissionLevel.READ;
    }

    @Override
    public ToolSchema schema() {
        return ToolSchema.object(
                Map.of("text", new ToolSchema.Property("string", "Raw XML")),
                List.of("text")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        try {
            String text = ToolArguments.requireString(arguments, "text");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return ToolResult.ok(name(), writer.toString(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.fail(name(), "Invalid XML: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
