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
public final class SerdeHistoricalImportController_ErrorResponseSerializer implements Serializer<HistoricalImportController.ErrorResponse>, ObjectSerializer<HistoricalImportController.ErrorResponse> {
  private static final String KEY_0 = "status";

  private static final Argument ARGUMENT_0 = Argument.INT.withName(SerdeHistoricalImportController_ErrorResponseSerializer.KEY_0);

  private static final String KEY_1 = "error";

  private static final Argument ARGUMENT_1 = Argument.STRING.withName(SerdeHistoricalImportController_ErrorResponseSerializer.KEY_1);

  private static final String KEY_2 = "detail";

  private static final Argument ARGUMENT_2 = Argument.STRING.withName(SerdeHistoricalImportController_ErrorResponseSerializer.KEY_2);

  public Serializer<HistoricalImportController.ErrorResponse> createSpecific(Serializer.EncoderContext context,
      Argument<? extends HistoricalImportController.ErrorResponse> type) throws SerdeException {
    return (Serializer<HistoricalImportController.ErrorResponse>) GeneratedSerdeFallbackUtil.withRuntimeObjectFallback(this, context, type);
  }

  public void serialize(Encoder encoder, Serializer.EncoderContext context, Argument type, HistoricalImportController.ErrorResponse value) throws
      IOException {
    Encoder objectEncoder = encoder.encodeObject(type);
    this.serializeInto(objectEncoder, context, type, value);
    objectEncoder.finishStructure();
  }

  public void serializeInto(Encoder encoder, Serializer.EncoderContext context, Argument type, HistoricalImportController.ErrorResponse value) throws
      IOException {
    encoder.encodeKey(SerdeHistoricalImportController_ErrorResponseSerializer.KEY_0);
    try {
      encoder.encodeInt(value.status());
    } catch (Throwable e0) {
      throw GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, SerdeHistoricalImportController_ErrorResponseSerializer.ARGUMENT_0);
    }
    encoder.encodeKey(SerdeHistoricalImportController_ErrorResponseSerializer.KEY_1);
    try {
      String value1 = value.error();
      if (value1 == null) {
        encoder.encodeNull();
      } else {
        encoder.encodeString(value1);
      }
    } catch (Throwable e0) {
      throw GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, SerdeHistoricalImportController_ErrorResponseSerializer.ARGUMENT_1);
    }
    encoder.encodeKey(SerdeHistoricalImportController_ErrorResponseSerializer.KEY_2);
    try {
      String value2 = value.detail();
      if (value2 == null) {
        encoder.encodeNull();
      } else {
        encoder.encodeString(value2);
      }
    } catch (Throwable e0) {
      throw GeneratedSerdeExceptionUtil.withPropertyPath(e0, type, SerdeHistoricalImportController_ErrorResponseSerializer.ARGUMENT_2);
    }
  }
}
