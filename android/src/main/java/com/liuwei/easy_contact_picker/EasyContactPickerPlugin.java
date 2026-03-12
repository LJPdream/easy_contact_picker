package com.liuwei.easy_contact_picker;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** EasyContactPickerPlugin */
public class EasyContactPickerPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

  private static final String CHANNEL = "plugins.flutter.io/easy_contact_picker";
  // 跳转原生选择联系人页面
  static final String METHOD_CALL_NATIVE = "selectContactNative";
  // 获取联系人列表
  static final String METHOD_CALL_LIST = "selectContactList";
  
  private MethodChannel channel;
  private Context context;
  private Activity activity;
  private ContactsCallBack contactsCallBack;
  private Result pendingResult;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), CHANNEL);
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    channel = null;
    context = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    binding.addActivityResultListener(this::onActivityResult);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    activity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    binding.addActivityResultListener(this::onActivityResult);
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
    if (call.method.equals(METHOD_CALL_NATIVE)) {
      pendingResult = result;
      contactsCallBack = new ContactsCallBack() {
        @Override
        void successWithMap(HashMap<String, String> map) {
          super.successWithMap(map);
          pendingResult = null;
          result.success(map);
        }

        @Override
        void error() {
          super.error();
          pendingResult = null;
        }
      };
      intentToContact();
    } else if (call.method.equals(METHOD_CALL_LIST)) {
      contactsCallBack = new ContactsCallBack() {
        @Override
        void successWithList(List<HashMap> contacts) {
          super.successWithList(contacts);
          result.success(contacts);
        }

        @Override
        void error() {
          super.error();
        }
      };
      getContacts();
    } else {
      result.notImplemented();
    }
  }

  /** 跳转到联系人界面. */
  private void intentToContact() {
    if (activity == null) {
      return;
    }
    Intent intent = new Intent();
    intent.setAction("android.intent.action.PICK");
    intent.addCategory("android.intent.category.DEFAULT");
    intent.setType("vnd.android.cursor.dir/phone_v2");
    activity.startActivityForResult(intent, 0x30);
  }

  private void getContacts() {
    if (activity == null && context == null) {
      return;
    }
    
    //（实际上就是"sort_key"字段） 出来是首字母
    final String PHONE_BOOK_LABEL = "phonebook_label";
    //需要查询的字段
    final String[] CONTACTOR_ION = new String[]{
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            PHONE_BOOK_LABEL
    };

    List contacts = new ArrayList<>();
    Uri uri = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
      uri = ContactsContract.CommonDataKinds.Contactables.CONTENT_URI;
    }
    
    ContentResolver resolver = (activity != null) ? activity.getContentResolver() : context.getContentResolver();
    //获取联系人。按首字母排序
    Cursor cursor = resolver.query(uri, CONTACTOR_ION, null, null, ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY);
    if (cursor != null) {
      while (cursor.moveToNext()) {
        HashMap<String, String> map = new HashMap<String, String>();
        String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
        String phoneNum = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
        String firstChar = cursor.getString(cursor.getColumnIndex(PHONE_BOOK_LABEL));
        map.put("fullName", name);
        map.put("phoneNumber", phoneNum);
        map.put("firstLetter", firstChar);

        contacts.add(map);
      }
      cursor.close();
      contactsCallBack.successWithList(contacts);
    }
  }

  private boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == 0x30) {
      if (data != null && activity != null) {
        Uri uri = data.getData();
        String phoneNum = null;
        String contactName = null;
        // 创建内容解析者
        ContentResolver contentResolver = activity.getContentResolver();
        Cursor cursor = null;
        if (uri != null) {
          cursor = contentResolver.query(uri,
                  new String[]{"display_name", "data1"}, null, null, null);
        }
        if (cursor != null) {
          while (cursor.moveToNext()) {
            contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            phoneNum = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
          }
          cursor.close();
        }
        //  把电话号码中的  -  符号 替换成空格
        if (phoneNum != null) {
          phoneNum = phoneNum.replaceAll("-", " ");
          // 空格去掉  为什么不直接-替换成"" 因为测试的时候发现还是会有空格 只能这么处理
          phoneNum = phoneNum.replaceAll(" ", "");
        }
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("fullName", contactName);
        map.put("phoneNumber", phoneNum);
        contactsCallBack.successWithMap(map);
      }
      return true;
    }
    return false;
  }

  /** 获取通讯录回调. */
  public abstract class ContactsCallBack {
    void successWithList(List<HashMap> contacts) {};
    void successWithMap(HashMap<String, String> map) {};
    void error() {};
  }
}
