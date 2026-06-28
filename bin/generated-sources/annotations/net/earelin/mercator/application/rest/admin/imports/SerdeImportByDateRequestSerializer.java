package net.earelin.mercator.application.rest.admin.imports;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.GeneratedSerdeExceptionUtil;
import io.micronaut.serde.util.GeneratedSerdeFallbackUtil;
import java.io.IOException;
import java.lang.String;
import java.lang.Throwable;
import javax.annotation.processing.Generated;

@Prototype
@Generated("Micronaut")
public final class SerdeImportByDateRequestSerializer implements Serializer<ImportByDateRequest>, ObjectSerializer<ImportByDateRequest> {
  private static final String KEY_0 = "date";

  private static final Argument ARGUMENT_0 = Argument.STRING.withName(SerdeImportByDateRequestSerializer.KEY_0);

  public Serializer<ImportByDateRequest> createSpecific(Serializer.EncoderContext context, Argument<? extends ImportByDateRequest> type) throws
      SerdeException {
    return (Serializer<ImportByDateRequest>) GeneratedSerdeFallbackUtil.withRuntimeObjectFallback(this, context, type);
  }

  public void serialize(Encoder encoder, Serializer.EncoderContext context, Argument type, ImportByDateRequest value) throws IOException {
    Encoder objectEncoder = encoder.encodeObject(type);
    this.serializeInto(objectEncoder, context, type, value);
    objectEncoder.finishStructure();
  }

  public void serializeInto(Encoder encoder, Serializer.EncoderContext context, Argument type, ImportByDateRequest value) throws IOException {
    encoder.encodeKey(SerdeImportByDateRequestSerializer.KEY_0);
    try {
      String value0 = value.date();
      if (value0 == null) {
        encoder.encodeNull();
      } else {
        encoder.encodeString(value0);
      }
    } catch (Throwable e0) {
      throw GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, SerdeImportByDateRequestSerializer.ARGUMENT_0);
    }
  }
}
