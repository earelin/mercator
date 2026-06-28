package net.earelin.mercator.application.rest.admin.imports;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.GeneratedSerdeExceptionUtil;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import java.io.IOException;
import java.lang.String;
import java.lang.Throwable;
import javax.annotation.processing.Generated;

@Prototype
@Generated("Micronaut")
public final class SerdeImportByMonthRequestDeserializer implements Deserializer<ImportByMonthRequest> {
  private static final String KEY_0 = "month";

  private static final Argument ARGUMENT_0 = Argument.STRING.withName(SerdeImportByMonthRequestDeserializer.KEY_0);

  private final boolean ignoreUnknown;

  public SerdeImportByMonthRequestDeserializer(@Parameter Deserializer.DecoderContext context, @Parameter Argument type) throws SerdeException {
    this.ignoreUnknown = GeneratedSerdeExceptionUtil.ignoreUnknown(context);
  }

  public Deserializer<ImportByMonthRequest> createSpecific(Deserializer.DecoderContext context, Argument type) throws SerdeException {
    return (Deserializer<ImportByMonthRequest>) GeneratedSerdeFallbackUtil.withRuntimeObjectFallback(this, context, type);
  }

  public ImportByMonthRequest deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument type) throws IOException {
    Decoder objectDecoder = decoder.decodeObject(type);
    boolean seenProperty0 = false;
    String propertyValue0 = null;
    while (true) {
      String key = objectDecoder.decodeKey();
      if (key == null) {
        objectDecoder.finishStructure();
        return new net.earelin.mercator.application.rest.admin.imports.ImportByMonthRequest(propertyValue0);
      }
      if (key.equals(SerdeImportByMonthRequestDeserializer.KEY_0)) {
        if (seenProperty0) {
          throw GeneratedSerdeExceptionUtil.duplicateProperty(type, SerdeImportByMonthRequestDeserializer.ARGUMENT_0);
        } else {
          seenProperty0 = true;
          try {
            propertyValue0 = (String) objectDecoder.decodeStringNullable();
          } catch (Throwable e0) {
            throw GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, SerdeImportByMonthRequestDeserializer.ARGUMENT_0);
          }
        }
      } else {
        if (this.ignoreUnknown) {
          objectDecoder.skipValue();
        } else {
          throw GeneratedSerdeExceptionUtil.unknownProperty(type, Argument.OBJECT_ARGUMENT.withName(key));
        }
      }
    }
  }
}
