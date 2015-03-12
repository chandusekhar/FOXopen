package net.foxopen.fox.thread.storage;

import net.foxopen.fox.thread.ActionRequestContext;
import net.foxopen.fox.thread.RequestContext;

import java.sql.Blob;
import java.sql.Clob;

/**
 * An object which can create TempResources and provide storage location definitions for temporary storage locations.
 * Implementors should support both Blob and Clob resource types.
 */
public interface TempResourceProvider {

  /**
   * Gets a TempResource WorkingStorageLocation for accessing a Clob.
   * @return
   */
  public TempResource<Clob> getClobTempResource();

  /**
   * Gets a TempResource WorkingStorageLocation for accessing a Blob. This signature auto-generates an ID.
   * @return
   */
  public TempResource<Blob> getBlobTempResource();

  /**
   * Gets a TempResource WorkingStorageLocation for accessing a Blob, using a known ID. This does not have to actually exist
   * at this point; the TempResource can be used as for bootstrapping.
   * @param pResourceId Temporary resource ID.
   * @return
   */
  public TempResource<Blob> getBlobTempResource(String pResourceId);

  /**
   * Creates a temporary resource which will be generated by the given TempResourceGenerator. The generator should be
   * serialised for later retrieval.
   * @param pRequestContext
   * @param pResourceId ID to assign to the newly created TempResource.
   * @param pGenerator Object responsible.
   * @param pCache If true, data should be cached locally to avoid trips to the database when the resource is requested.
   * @return A new TempResource with content to be generated by a TempResourceGenerator.
   */
  public TempResource<?> createTempResource(ActionRequestContext pRequestContext, String pResourceId, TempResourceGenerator pGenerator, boolean pCache);

  /**
   * Gets a previously created TempResourceGeneator which is ready to stream ouput.
   * @param pRequestContext
   * @param pResourceId Temp resource ID.
   * @param pDestination Destiniation for the temp resource's bytes.
   */
  public TempResourceGenerator getExistingResourceGenerator(RequestContext pRequestContext, String pResourceId);

  /**
   * Gets a StorageLocation definition for the given LOB type. Both the Blob and Clob class should be supported. Other
   * types may return an error if the provider does not support them.
   * @return
   */
  public FileStorageLocation getTempStorageLocationForLOBType(Class pLOBType);

}
